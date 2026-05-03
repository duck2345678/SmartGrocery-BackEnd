package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AdminOpsOrderDto;
import com.smartgrocery.backend.dto.AdminOverrideRequest;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AdminOverrideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin - Overrides", description = "Break-glass override cho điều phối đơn")
@RequiredArgsConstructor
public class AdminOverrideController {

    private final AdminOverrideService adminOverrideService;

    private void assertAdminRole() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Force release đơn về lại queue (PENDING)")
    @PostMapping("/{id}/force-release")
    public ResponseEntity<AdminOpsOrderDto> forceRelease(
            @AuthenticationPrincipal User admin,
            @PathVariable("id") Long id,
            @RequestBody AdminOverrideRequest request
    ) {
        assertAdminRole();
        if (request == null) throw new IllegalArgumentException("Thiếu payload");
        Order saved = adminOverrideService.forceRelease(admin, id, request.getReason());
        return ResponseEntity.ok(toDto(saved));
    }

    @Operation(summary = "Emergency assign đơn cho STAFF (ASSIGNED + lease 10 phút)")
    @PostMapping("/{id}/emergency-assign")
    public ResponseEntity<AdminOpsOrderDto> emergencyAssign(
            @AuthenticationPrincipal User admin,
            @PathVariable("id") Long id,
            @RequestBody AdminOverrideRequest request
    ) {
        assertAdminRole();
        if (request == null) throw new IllegalArgumentException("Thiếu payload");
        Order saved = adminOverrideService.emergencyAssign(admin, id, request.getStaffId(), request.getReason());
        return ResponseEntity.ok(toDto(saved));
    }

    private AdminOpsOrderDto toDto(Order o) {
        LocalDateTime now = LocalDateTime.now();
        Integer minutesSinceUpdate = null;
        if (o.getUpdatedAt() != null) {
            minutesSinceUpdate = (int) java.time.Duration.between(o.getUpdatedAt(), now).toMinutes();
        }

        return AdminOpsOrderDto.builder()
                .orderId(o.getId())
                .orderNumber(o.getOrderNumber())
                .status(o.getStatus())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .assigneeId(o.getAssignee() != null ? o.getAssignee().getId() : null)
                .assigneeName(o.getAssignee() != null ? o.getAssignee().getFullName() : null)
                .leaseExpiresAt(o.getLeaseExpiresAt())
                .minutesToSla(null)
                .minutesSinceUpdate(minutesSinceUpdate)
                .build();
    }
}

