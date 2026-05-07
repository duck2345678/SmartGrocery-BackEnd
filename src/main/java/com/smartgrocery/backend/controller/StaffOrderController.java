package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AssignOrderResponse;
import com.smartgrocery.backend.dto.CompletePickingRequest;
import com.smartgrocery.backend.dto.StaffPickOrderDto;
import com.smartgrocery.backend.dto.StaffOrderDto;
import com.smartgrocery.backend.dto.StaffPerformanceDailyDto;
import com.smartgrocery.backend.dto.StaffPerformanceSummaryDto;
import com.smartgrocery.backend.dto.StaffSubstitutionOptionDto;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.StaffOrderFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/staff/orders")
@Tag(name = "Staff - Orders", description = "Luồng nhặt hàng dành cho nhân viên")
@RequiredArgsConstructor
public class StaffOrderController {

    private final StaffOrderFlowService staffOrderFlowService;

    private void assertStaffRole() {
        if (!SecurityUtils.hasAnyRole("STAFF", "ADMIN")) {
            Long userId = SecurityUtils.getCurrentUserId();
            System.err.println("[AUTH] Access Denied for User ID: " + userId + ". Required roles: [STAFF, ADMIN]");
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Danh sách đơn chờ nhận (không bị lease hoặc lease đã hết hạn)")
    @GetMapping("/queue")
    public ResponseEntity<List<StaffOrderDto>> getQueue(@AuthenticationPrincipal User user) {
        assertStaffRole();
        return ResponseEntity.ok(staffOrderFlowService.getQueue(user));
    }

    @Operation(summary = "Đơn đang được phân cho chính staff hiện tại (ASSIGNED/PICKING + lease còn hạn)")
    @GetMapping("/my-active")
    public ResponseEntity<StaffOrderDto> getMyActive(@AuthenticationPrincipal User user) {
        assertStaffRole();
        return ResponseEntity.of(staffOrderFlowService.getMyActiveOrder(user));
    }

    @Operation(summary = "Nhận đơn (assign lease 10 phút)")
    @PostMapping("/{orderId}/assign")
    public ResponseEntity<AssignOrderResponse> assign(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId
    ) {
        assertStaffRole();
        return ResponseEntity.ok(staffOrderFlowService.assignOrder(orderId, user));
    }

    @Operation(summary = "Heartbeat gia hạn lease khi đang pick")
    @PostMapping("/{orderId}/heartbeat")
    public ResponseEntity<AssignOrderResponse> heartbeat(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId
    ) {
        assertStaffRole();
        return ResponseEntity.ok(staffOrderFlowService.heartbeat(orderId, user));
    }

    @Operation(summary = "Thả đơn đã nhận")
    @PostMapping("/{orderId}/release")
    public ResponseEntity<AssignOrderResponse> release(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId
    ) {
        assertStaffRole();
        return ResponseEntity.ok(staffOrderFlowService.release(orderId, user));
    }

    @Operation(summary = "Lấy pick-list theo thứ tự kệ hàng (aisle_location ASC)")
    @GetMapping("/{orderId}/pick-list")
    public ResponseEntity<StaffPickOrderDto> getPickList(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId
    ) {
        assertStaffRole();
        return ResponseEntity.ok(staffOrderFlowService.getPickList(orderId, user));
    }

    @Operation(summary = "Chốt nhặt hàng theo batch payload")
    @PostMapping("/{orderId}/complete-picking")
    public ResponseEntity<StaffPickOrderDto> completePicking(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId,
            @RequestBody CompletePickingRequest request
    ) {
        assertStaffRole();
        return ResponseEntity.ok(staffOrderFlowService.completePicking(orderId, user, request));
    }

    @Operation(summary = "Gợi ý hàng thay thế (lọc theo category, giá <= giá gốc, stock > 0)")
    @GetMapping("/{orderId}/substitutions")
    public ResponseEntity<List<StaffSubstitutionOptionDto>> getSubstitutions(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId,
            @RequestParam Long orderItemId
    ) {
        assertStaffRole();
        return ResponseEntity.ok(staffOrderFlowService.getSubstitutions(orderId, orderItemId, user));
    }

    @Operation(summary = "Hiệu suất theo ngày: số đơn hoàn thành + danh sách chi tiết")
    @GetMapping("/performance/daily")
    public ResponseEntity<StaffPerformanceDailyDto> performanceDaily(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        assertStaffRole();
        return ResponseEntity.ok(staffOrderFlowService.getPerformanceDaily(user, date));
    }

    @Operation(summary = "Hiệu suất tổng quan theo tuần/tháng cho ngày được chọn")
    @GetMapping("/performance/summary")
    public ResponseEntity<StaffPerformanceSummaryDto> performanceSummary(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        assertStaffRole();
        return ResponseEntity.ok(staffOrderFlowService.getPerformanceSummary(user, date));
    }
}
