package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AdminOpsOrderDto;
import com.smartgrocery.backend.dto.AdminOrderDashboardSummaryDto;
import com.smartgrocery.backend.dto.AdminOrderSummaryDto;
import com.smartgrocery.backend.dto.AdminOverrideRequest;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AdminOrderDashboardService;
import com.smartgrocery.backend.service.AdminOverrideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin - Orders", description = "Admin order dashboard and overrides")
@RequiredArgsConstructor
public class AdminOverrideController {

    private final AdminOverrideService adminOverrideService;
    private final AdminOrderDashboardService adminOrderDashboardService;

    private void assertAdminRole() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "List recent orders")
    @GetMapping
    public ResponseEntity<Page<AdminOrderSummaryDto>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        assertAdminRole();
        return ResponseEntity.ok(adminOrderDashboardService.listRecentOrders(page, size, search, status, from, to, sortBy, sortDir));
    }

    @Operation(summary = "Order dashboard summary")
    @GetMapping("/dashboard-summary")
    public ResponseEntity<AdminOrderDashboardSummaryDto> dashboardSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        assertAdminRole();
        return ResponseEntity.ok(adminOrderDashboardService.getDashboardSummary(from, to));
    }

    @Operation(summary = "Force release order back to queue")
    @PostMapping("/{id}/force-release")
    public ResponseEntity<AdminOpsOrderDto> forceRelease(
            @AuthenticationPrincipal User admin,
            @PathVariable("id") Long id,
            @RequestBody AdminOverrideRequest request
    ) {
        assertAdminRole();
        if (request == null) throw new IllegalArgumentException("Missing payload");
        Order saved = adminOverrideService.forceRelease(admin, id, request.getReason());
        return ResponseEntity.ok(toDto(saved));
    }

    @Operation(summary = "Emergency assign order to staff")
    @PostMapping("/{id}/emergency-assign")
    public ResponseEntity<AdminOpsOrderDto> emergencyAssign(
            @AuthenticationPrincipal User admin,
            @PathVariable("id") Long id,
            @RequestBody AdminOverrideRequest request
    ) {
        assertAdminRole();
        if (request == null) throw new IllegalArgumentException("Missing payload");
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
