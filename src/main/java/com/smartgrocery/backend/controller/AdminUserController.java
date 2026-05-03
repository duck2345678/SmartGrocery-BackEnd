package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AdminUserDeleteRequest;
import com.smartgrocery.backend.dto.AdminUserDto;
import com.smartgrocery.backend.dto.AdminUserStatusRequest;
import com.smartgrocery.backend.dto.AdminUserUpsertRequest;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AdminUserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin - Users", description = "Quản lý tài khoản customer/staff/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserManagementService adminUserManagementService;

    private void assertAdminOrStaff() {
        if (!SecurityUtils.hasAnyRole("ADMIN", "STAFF")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Danh sách users có filter role/status/date")
    @GetMapping
    public ResponseEntity<Page<AdminUserDto>> list(
            @AuthenticationPrincipal User actor,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        assertAdminOrStaff();
        return ResponseEntity.ok(adminUserManagementService.search(actor, role, status, createdFrom, createdTo, page, size));
    }

    @Operation(summary = "Tạo user")
    @PostMapping
    public ResponseEntity<AdminUserDto> create(@AuthenticationPrincipal User actor, @RequestBody AdminUserUpsertRequest request) {
        assertAdminOrStaff();
        return ResponseEntity.ok(adminUserManagementService.create(actor, request));
    }

    @Operation(summary = "Cập nhật user")
    @PutMapping("/{id}")
    public ResponseEntity<AdminUserDto> update(
            @AuthenticationPrincipal User actor,
            @PathVariable("id") Long id,
            @RequestBody AdminUserUpsertRequest request
    ) {
        assertAdminOrStaff();
        return ResponseEntity.ok(adminUserManagementService.update(actor, id, request));
    }

    @Operation(summary = "Ban/Unban tài khoản")
    @PostMapping("/{id}/status")
    public ResponseEntity<AdminUserDto> setStatus(
            @AuthenticationPrincipal User actor,
            @PathVariable("id") Long id,
            @RequestBody AdminUserStatusRequest request
    ) {
        assertAdminOrStaff();
        return ResponseEntity.ok(adminUserManagementService.setStatus(actor, id, request.getStatus(), request.getReason()));
    }

    @Operation(summary = "Soft delete tài khoản")
    @DeleteMapping("/{id}")
    public ResponseEntity<AdminUserDto> softDelete(
            @AuthenticationPrincipal User actor,
            @PathVariable("id") Long id,
            @RequestBody(required = false) AdminUserDeleteRequest request
    ) {
        assertAdminOrStaff();
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(adminUserManagementService.softDelete(actor, id, reason));
    }
}
