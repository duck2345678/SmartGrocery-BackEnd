package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.CreateIssueRequest;
import com.smartgrocery.backend.dto.IssueDto;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.IssueService;
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
@RequestMapping("/api/v1/staff/issues")
@Tag(name = "Staff - Issues", description = "Báo cáo sự cố vận hành kho")
@RequiredArgsConstructor
public class StaffIssueController {

    private final IssueService issueService;

    private void assertStaffRole() {
        if (!SecurityUtils.hasAnyRole("STAFF", "ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Tạo báo cáo sự cố (atomic: tạo issue + set order ON_HOLD)")
    @PostMapping
    public ResponseEntity<IssueDto> create(
            @AuthenticationPrincipal User user,
            @RequestBody CreateIssueRequest request
    ) {
        assertStaffRole();
        return ResponseEntity.ok(issueService.create(user, request));
    }

    @Operation(summary = "Danh sách sự cố của tôi")
    @GetMapping("/my")
    public ResponseEntity<List<IssueDto>> my(@AuthenticationPrincipal User user) {
        assertStaffRole();
        return ResponseEntity.ok(issueService.myIssues(user));
    }

    @Operation(summary = "Danh sách sự cố theo order")
    @GetMapping
    public ResponseEntity<List<IssueDto>> byOrder(@RequestParam Long orderId) {
        assertStaffRole();
        return ResponseEntity.ok(issueService.byOrder(orderId));
    }
}
