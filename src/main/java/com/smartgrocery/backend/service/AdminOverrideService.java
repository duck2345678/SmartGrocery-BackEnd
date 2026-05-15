package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.OrderRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AdminOverrideService {

    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private final Map<String, Long> lastActionAt = new ConcurrentHashMap<>();

    @Transactional
    public Order forceRelease(User admin, Long orderId, String reason) {
        requireReason(reason);
        applyCooldown("FORCE_RELEASE", orderId);

        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        JsonNode before = snapshot(order);

        if ("CANCELLED".equalsIgnoreCase(order.getStatus()) || "DELIVERED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Không thể force-release đơn ở trạng thái " + order.getStatus());
        }

        order.setStatus("PENDING");
        order.setAssignee(null);
        order.setLeaseExpiresAt(null);
        Order saved = orderRepository.save(order);

        auditService.log(admin, "FORCE_RELEASE", "ORDER", orderId, reason, before, snapshot(saved));
        return saved;
    }

    @Transactional
    public Order emergencyAssign(User admin, Long orderId, Long staffId, String reason) {
        requireReason(reason);
        if (staffId == null) throw new IllegalArgumentException("Thiếu staffId");
        applyCooldown("EMERGENCY_ASSIGN", orderId);

        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        JsonNode before = snapshot(order);

        if ("CANCELLED".equalsIgnoreCase(order.getStatus()) || "DELIVERED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Không thể emergency-assign đơn ở trạng thái " + order.getStatus());
        }

        User staff = userRepository.findById(staffId).orElseThrow(() -> new RuntimeException("Staff user not found"));
        if (staff.getRole() == null || staff.getRole().getName() == null || !"STAFF".equalsIgnoreCase(staff.getRole().getName())) {
            throw new IllegalArgumentException("userId không phải STAFF");
        }

        order.setStatus("ASSIGNED");
        order.setAssignee(staff);
        order.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(30));
        Order saved = orderRepository.save(order);

        auditService.log(admin, "EMERGENCY_ASSIGN", "ORDER", orderId, reason, before, snapshot(saved));
        return saved;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Thiếu reason");
        }
        if (reason.trim().length() < 5) {
            throw new IllegalArgumentException("Reason quá ngắn");
        }
    }

    private void applyCooldown(String action, Long orderId) {
        long now = System.currentTimeMillis();
        String key = action + ":" + orderId;
        Long last = lastActionAt.get(key);
        if (last != null && now - last < COOLDOWN.toMillis()) {
            throw new IllegalArgumentException("Cooldown: vui lòng chờ trước khi thao tác lại");
        }
        lastActionAt.put(key, now);
    }

    private ObjectNode snapshot(Order order) {
        ObjectNode n = objectMapper.createObjectNode();
        if (order == null) return n;
        n.put("orderId", order.getId() != null ? order.getId() : 0);
        n.put("status", order.getStatus() != null ? order.getStatus() : "");
        n.put("assigneeId", order.getAssignee() != null && order.getAssignee().getId() != null ? order.getAssignee().getId() : null);
        n.put("leaseExpiresAt", order.getLeaseExpiresAt() != null ? order.getLeaseExpiresAt().toString() : null);
        n.put("totalAmount", order.getTotalAmount() != null ? order.getTotalAmount().toPlainString() : "0");
        n.put("updatedAt", order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null);
        return n;
    }
}

