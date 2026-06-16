package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AdminShiftRequestItemDto;
import com.smartgrocery.backend.dto.AdminShiftRequestStatusRequest;
import com.smartgrocery.backend.dto.AdminShiftScheduleItemDto;
import com.smartgrocery.backend.dto.ApiResponse;
import com.smartgrocery.backend.dto.ShiftRequestDto;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.ShiftRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/shift-requests")
@Tag(name = "Admin - Shift Requests", description = "Duyệt đăng ký ca làm")
@RequiredArgsConstructor
public class AdminShiftRequestController {

    private final ShiftRequestService shiftRequestService;

    private void assertAdmin() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Danh sách đơn đăng ký ca theo khoảng ngày")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminShiftRequestItemDto>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status
    ) {
        assertAdmin();
        return ResponseEntity.ok(ApiResponse.success(shiftRequestService.adminList(from, to, status)));
    }

    @Operation(summary = "Danh sách lịch làm việc đã duyệt")
    @GetMapping("/schedules")
    public ResponseEntity<ApiResponse<List<AdminShiftScheduleItemDto>>> schedules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        assertAdmin();
        return ResponseEntity.ok(ApiResponse.success(shiftRequestService.adminScheduleList(from, to)));
    }

    @Operation(summary = "Duyệt/Từ chối đơn đăng ký ca")
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ShiftRequestDto>> updateStatus(
            @PathVariable("id") Long id,
            @RequestBody AdminShiftRequestStatusRequest request
    ) {
        assertAdmin();
        return ResponseEntity.ok(ApiResponse.success(shiftRequestService.adminUpdateStatus(id, request)));
    }
}

