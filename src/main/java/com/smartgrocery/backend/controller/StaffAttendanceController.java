package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.*;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AttendanceService;
import com.smartgrocery.backend.service.AttendanceStatisticsService;
import com.smartgrocery.backend.service.ShiftRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/staff/attendance")
@Tag(name = "Staff - Attendance", description = "Chấm công nhân viên")
@RequiredArgsConstructor
public class StaffAttendanceController {

    private final AttendanceService attendanceService;
    private final ShiftRequestService shiftRequestService;
    private final AttendanceStatisticsService attendanceStatisticsService;

    private void assertStaffRole() {
        if (!SecurityUtils.hasAnyRole("STAFF", "ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Lấy cấu hình giờ làm của các ca (S, C, G)")
    @GetMapping("/shift-config")
    public ResponseEntity<ApiResponse<List<ShiftConfigDto>>> getShiftConfig() {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getShiftConfig()));
    }

    @Operation(summary = "Vào ca")
    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<AttendanceRecordDto>> checkIn(
            @AuthenticationPrincipal User user,
            @RequestBody AttendanceCheckRequest request
    ) {
        assertStaffRole();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.checkIn(user, request)));
    }

    @Operation(summary = "Ra ca")
    @PostMapping("/check-out")
    public ResponseEntity<ApiResponse<AttendanceRecordDto>> checkOut(
            @AuthenticationPrincipal User user,
            @RequestBody AttendanceCheckRequest request
    ) {
        assertStaffRole();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.checkOut(user, request)));
    }

    @Operation(summary = "Trạng thái hôm nay")
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<StaffAttendanceTodayDto>> getTodayStatus(@AuthenticationPrincipal User user) {
        assertStaffRole();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getTodayStatus(user)));
    }

    @Operation(summary = "Lịch sử chấm công theo tháng")
    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<AttendanceDayDto>>> getMonthlyCalendar(
            @AuthenticationPrincipal User user,
            @RequestParam int year,
            @RequestParam int month
    ) {
        assertStaffRole();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getMonthlyCalendar(user, year, month)));
    }

    @Operation(summary = "Thống kê chấm công theo tháng")
    @GetMapping("/monthly-stats")
    public ResponseEntity<ApiResponse<AttendanceMonthlyStatsDto>> getMonthlyStats(
            @AuthenticationPrincipal User user,
            @RequestParam int year,
            @RequestParam int month
    ) {
        assertStaffRole();
        return ResponseEntity.ok(ApiResponse.success(attendanceStatisticsService.getMonthlyStats(user, year, month)));
    }

    @Operation(summary = "Đăng ký ca làm (tương lai)")
    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<ShiftRequestDto>> createShiftRequest(
            @AuthenticationPrincipal User user,
            @RequestBody ShiftRequestCreateRequest request
    ) {
        assertStaffRole();
        return ResponseEntity.ok(ApiResponse.success(shiftRequestService.createOrUpdateRequest(user, request)));
    }

    @Operation(summary = "Hủy đăng ký ca (chỉ khi PENDING)")
    @DeleteMapping("/requests/{id}")
    public ResponseEntity<ApiResponse<ShiftRequestDto>> cancelShiftRequest(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id
    ) {
        assertStaffRole();
        return ResponseEntity.ok(ApiResponse.success(shiftRequestService.cancelRequest(user, id)));
    }
}
