package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.IssueDto;
import com.smartgrocery.backend.dto.ResolveIssueRequest;
import com.smartgrocery.backend.dto.StaffSubstitutionOptionDto;
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
@RequestMapping("/api/v1/admin/issues")
@Tag(name = "Admin - Issues", description = "Duyệt và xử lý sự cố vận hành")
@RequiredArgsConstructor
public class AdminIssueController {

    private final IssueService issueService;

    private void assertAdminRole() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Danh sách issue OPEN cho admin")
    @GetMapping
    public ResponseEntity<List<IssueDto>> openIssues() {
        assertAdminRole();
        return ResponseEntity.ok(issueService.openIssues());
    }

    @Operation(summary = "Chi tiết issue")
    @GetMapping("/{id}")
    public ResponseEntity<IssueDto> detail(@PathVariable("id") Long id) {
        assertAdminRole();
        return ResponseEntity.ok(issueService.getById(id));
    }

    @Operation(summary = "Resolve issue và cập nhật order state machine")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<IssueDto> resolve(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal User admin,
            @RequestBody ResolveIssueRequest request
    ) {
        assertAdminRole();
        return ResponseEntity.ok(issueService.resolveIssue(id, admin, request));
    }

    @Operation(summary = "Gợi ý sản phẩm thay thế + tìm kiếm")
    @GetMapping("/{id}/substitutions")
    public ResponseEntity<List<StaffSubstitutionOptionDto>> substitutions(
            @PathVariable("id") Long id,
            @RequestParam(value = "q", required = false) String keyword
    ) {
        assertAdminRole();
        return ResponseEntity.ok(issueService.getSubstituteOptions(id, keyword));
    }
}
