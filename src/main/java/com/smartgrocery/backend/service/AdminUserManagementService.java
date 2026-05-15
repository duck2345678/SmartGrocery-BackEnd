package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartgrocery.backend.dto.AdminUserDto;
import com.smartgrocery.backend.dto.AdminUserUpsertRequest;
import com.smartgrocery.backend.entity.Role;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.RoleRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminUserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AccountEmailService accountEmailService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<AdminUserDto> search(User actor, String role, String status, LocalDate createdFrom, LocalDate createdTo, int page, int size) {
        boolean isAdmin = hasRole(actor, "ADMIN");
        boolean isStaff = hasRole(actor, "STAFF");
        if (!isAdmin && !isStaff) {
            throw new IllegalArgumentException("Role not allowed");
        }
        if (isStaff && role != null && !role.isBlank() && !"CUSTOMER".equalsIgnoreCase(role.trim())) {
            throw new IllegalArgumentException("Staff can only filter CUSTOMER");
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Specification<User> spec = Specification.where(null);
        if (role != null && !role.isBlank()) {
            String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, q, cb) -> cb.equal(cb.upper(root.join("role").get("name")), normalizedRole));
        }
        if (status != null && !status.isBlank()) {
            String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, q, cb) -> cb.equal(cb.upper(root.get("status")), normalizedStatus));
        }
        if (createdFrom != null) {
            LocalDateTime from = createdFrom.atStartOfDay();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (createdTo != null) {
            LocalDateTime to = createdTo.plusDays(1).atStartOfDay();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("createdAt"), to));
        }
        if (isStaff) {
            spec = spec.and((root, q, cb) -> cb.equal(cb.upper(root.join("role").get("name")), "CUSTOMER"));
        }
        return userRepository.findAll(spec, PageRequest.of(safePage, safeSize)).map(this::toDto);
    }

    @Transactional
    public AdminUserDto create(User actor, AdminUserUpsertRequest request) {
        boolean isAdmin = hasRole(actor, "ADMIN");
        boolean isStaff = hasRole(actor, "STAFF");
        if (!isAdmin && !isStaff) throw new IllegalArgumentException("Role not allowed");

        String roleName = required(request.getRoleName(), "roleName").toUpperCase(Locale.ROOT);
        if (isStaff && !"CUSTOMER".equals(roleName)) {
            throw new IllegalArgumentException("Staff can only create CUSTOMER");
        }
        validatePassword(request.getPassword(), true);
        validateUnique(null, request.getEmail(), request.getPhone());
        Role role = roleRepository.findByName(roleName).orElseThrow(() -> new IllegalArgumentException("Invalid role"));

        User created = User.builder()
                .fullName(required(request.getFullName(), "fullName"))
                .email(required(request.getEmail(), "email"))
                .phone(normalizeNullable(request.getPhone()))
                .passwordHash(passwordEncoder.encode(request.getPassword().trim()))
                .role(role)
                .status(normalizeStatus(request.getStatus()))
                .avatarUrl(normalizeNullable(request.getAvatarUrl()))
                .build();
        User saved = userRepository.save(created);
        auditService.log(actor, "USER_CREATE", "USER", saved.getId(), reasonOrDefault(request.getReason(), "Create user"), null, snapshot(saved));
        return toDto(saved);
    }

    @Transactional
    public AdminUserDto update(User actor, Long id, AdminUserUpsertRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        boolean isAdmin = hasRole(actor, "ADMIN");
        boolean isStaff = hasRole(actor, "STAFF");
        if (!isAdmin && !isStaff) throw new IllegalArgumentException("Role not allowed");
        if (isStaff && !hasRole(user, "CUSTOMER")) {
            throw new IllegalArgumentException("Staff can only edit CUSTOMER");
        }
        JsonNode before = snapshot(user);

        if (request.getRoleName() != null && !request.getRoleName().isBlank()) {
            String roleName = request.getRoleName().trim().toUpperCase(Locale.ROOT);
            if (isStaff && !"CUSTOMER".equals(roleName)) {
                throw new IllegalArgumentException("Staff cannot change role away from CUSTOMER");
            }
            Role role = roleRepository.findByName(roleName).orElseThrow(() -> new IllegalArgumentException("Invalid role"));
            user.setRole(role);
        }
        if (request.getFullName() != null) user.setFullName(required(request.getFullName(), "fullName"));
        if (request.getEmail() != null) user.setEmail(required(request.getEmail(), "email"));
        if (request.getPhone() != null) user.setPhone(normalizeNullable(request.getPhone()));
        if (request.getAvatarUrl() != null) user.setAvatarUrl(normalizeNullable(request.getAvatarUrl()));
        if (request.getStatus() != null && !request.getStatus().isBlank()) user.setStatus(normalizeStatus(request.getStatus()));
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            validatePassword(request.getPassword(), false);
            user.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
        }
        validateUnique(user.getId(), user.getEmail(), user.getPhone());
        User saved = userRepository.save(user);
        auditService.log(actor, "USER_UPDATE", "USER", saved.getId(), reasonOrDefault(request.getReason(), "Update user"), before, snapshot(saved));
        return toDto(saved);
    }

    @Transactional
    public AdminUserDto setStatus(User actor, Long id, String status, String reason) {
        requireAdmin(actor);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        JsonNode before = snapshot(user);
        String next = normalizeStatus(status);
        if (!"ACTIVE".equals(next) && !"INACTIVE".equals(next)) {
            throw new IllegalArgumentException("Status must be ACTIVE/INACTIVE");
        }
        user.setStatus(next);
        User saved = userRepository.save(user);
        auditService.log(actor, "USER_STATUS", "USER", saved.getId(), reasonOrDefault(reason, "Change status"), before, snapshot(saved));
        accountEmailService.sendBanStatusEmail(saved, reason, "ACTIVE".equals(next));
        return toDto(saved);
    }

    @Transactional
    public AdminUserDto softDelete(User actor, Long id, String reason) {
        requireAdmin(actor);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        JsonNode before = snapshot(user);
        user.setStatus("DELETED");
        User saved = userRepository.save(user);
        auditService.log(actor, "USER_SOFT_DELETE", "USER", saved.getId(), reasonOrDefault(reason, "Soft delete"), before, snapshot(saved));
        return toDto(saved);
    }

    private void requireAdmin(User actor) {
        if (!hasRole(actor, "ADMIN")) {
            throw new IllegalArgumentException("Only admin can perform this action");
        }
    }

    private boolean hasRole(User user, String roleName) {
        return user != null && user.getRole() != null && roleName.equalsIgnoreCase(user.getRole().getName());
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private void validateUnique(Long currentId, String email, String phone) {
        if (email != null && !email.isBlank()) {
            boolean exists = currentId == null ? userRepository.existsByEmail(email.trim()) : userRepository.existsByEmailAndIdNot(email.trim(), currentId);
            if (exists) throw new IllegalArgumentException("Email already exists");
        }
        if (phone != null && !phone.isBlank()) {
            boolean exists = currentId == null
                    ? userRepository.findByPhone(phone.trim()).isPresent()
                    : userRepository.existsByPhoneAndIdNot(phone.trim(), currentId);
            if (exists) throw new IllegalArgumentException("Phone already exists");
        }
    }

    private void validatePassword(String password, boolean required) {
        if (!required && (password == null || password.isBlank())) return;
        String p = required(password, "password");
        if (p.length() < 8) throw new IllegalArgumentException("Password must be at least 8 chars");
        if (!p.matches(".*[A-Za-z].*") || !p.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must include letters and numbers");
        }
    }

    private String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) return "ACTIVE";
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim();
    }

    private String reasonOrDefault(String reason, String fallback) {
        if (reason == null || reason.isBlank()) return fallback;
        return reason.trim();
    }

    private AdminUserDto toDto(User user) {
        return AdminUserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private JsonNode snapshot(User user) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("id", user.getId());
        n.put("email", user.getEmail());
        n.put("phone", user.getPhone());
        n.put("fullName", user.getFullName());
        n.put("status", user.getStatus());
        n.put("roleName", user.getRole() != null ? user.getRole().getName() : null);
        n.put("avatarUrl", user.getAvatarUrl());
        return n;
    }
}
