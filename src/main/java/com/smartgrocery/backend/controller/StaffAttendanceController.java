package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.*;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AttendanceService;
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

    private void assertStaffRole() {
        if (!SecurityUtils.hasAnyRole("STAFF", "ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Lấy cấu hình giờ làm của các ca (S, C, G)")
    @GetMapping("/shift-config")
    public ResponseEntity<List<ShiftConfigDto>> getShiftConfig() {
        return ResponseEntity.ok(attendanceService.getShiftConfig());
    }

    @Operation(summary = "Vào ca")
    @PostMapping("/check-in")
    public ResponseEntity<AttendanceRecordDto> checkIn(
            @AuthenticationPrincipal User user,
            @RequestBody AttendanceCheckRequest request
    ) {
        assertStaffRole();
        return ResponseEntity.ok(attendanceService.checkIn(user, request));
    }

    @Operation(summary = "Ra ca")
    @PostMapping("/check-out")
    public ResponseEntity<AttendanceRecordDto> checkOut(
            @AuthenticationPrincipal User user,
            @RequestBody AttendanceCheckRequest request
    ) {
        assertStaffRole();
        return ResponseEntity.ok(attendanceService.checkOut(user, request));
    }

    @Operation(summary = "Trạng thái hôm nay")
    @GetMapping("/today")
    public ResponseEntity<StaffAttendanceTodayDto> getTodayStatus(@AuthenticationPrincipal User user) {
        assertStaffRole();
        return ResponseEntity.ok(attendanceService.getTodayStatus(user));
    }

    @Operation(summary = "Lịch sử chấm công theo tháng")
    @GetMapping("/calendar")
    public ResponseEntity<List<AttendanceDayDto>> getMonthlyCalendar(
            @AuthenticationPrincipal User user,
            @RequestParam int year,
            @RequestParam int month
    ) {
        assertStaffRole();
        return ResponseEntity.ok(attendanceService.getMonthlyCalendar(user, year, month));
    }
}
