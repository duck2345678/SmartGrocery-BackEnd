package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.*;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AttendanceStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/admin/attendance")
@Tag(name = "Admin - Attendance", description = "Thống kê chấm công cho admin")
@RequiredArgsConstructor
public class AdminAttendanceController {

    private final AttendanceStatisticsService attendanceStatisticsService;

    private void assertAdmin() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Thống kê chấm công theo tháng cho một nhân viên")
    @GetMapping("/monthly-stats/{userId}")
    public ResponseEntity<ApiResponse<AttendanceMonthlyStatsDto>> getMonthlyStatsForUser(
            @AuthenticationPrincipal User admin,
            @PathVariable Long userId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        assertAdmin();
        User target = User.builder().id(userId).build();
        return ResponseEntity.ok(ApiResponse.success(attendanceStatisticsService.getMonthlyStats(target, year, month)));
    }

    @Operation(summary = "Team schedule insights cho dashboard admin")
    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<AttendanceInsightDto>> getTeamInsights(
            @RequestParam int year,
            @RequestParam int month
    ) {
        assertAdmin();
        return ResponseEntity.ok(ApiResponse.success(attendanceStatisticsService.getMonthlyInsights(year, month)));
    }
}
