package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.ApiResponse;
import com.smartgrocery.backend.dto.OrderDto;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.OrderLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order - Lifecycle", description = "Luồng pack / deliver / complete")
@RequiredArgsConstructor
public class StaffOrderLifecycleController {

    private final OrderLifecycleService orderLifecycleService;

    private void assertStaffRole() {
        if (!SecurityUtils.hasAnyRole("STAFF", "ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Hoàn tất picking và chuyển READY_TO_SHIP")
    @PostMapping("/{id}/pack")
    public ResponseEntity<ApiResponse<OrderDto>> pack(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam String packingPhotoUrl
    ) {
        assertStaffRole();
        return ResponseEntity.ok(ApiResponse.success(orderLifecycleService.pack(id, user, packingPhotoUrl)));
    }

    @Operation(summary = "Bắt đầu giao hàng (kèm ảnh giao hàng)")
    @PostMapping("/{id}/deliver")
    public ResponseEntity<ApiResponse<OrderDto>> deliver(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam String deliveryPhotoUrl
    ) {
        assertStaffRole();
        return ResponseEntity.ok(ApiResponse.success(orderLifecycleService.deliver(id, user, deliveryPhotoUrl)));
    }

    @Operation(summary = "Hoàn tất giao hàng và chuyển DELIVERED")
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<OrderDto>> complete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        assertStaffRole();
        return ResponseEntity.ok(ApiResponse.success(orderLifecycleService.complete(id, user)));
    }
}
