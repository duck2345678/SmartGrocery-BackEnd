package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AdminOpsMonitorDto;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AdminOpsMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/admin/ops")
@Tag(name = "Admin - Ops Monitor", description = "SLA monitor cho điều phối")
@RequiredArgsConstructor
public class AdminOpsMonitorController {

    private final AdminOpsMonitorService adminOpsMonitorService;

    private void assertAdminRole() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "SLA monitor: stagnant orders + stalled staff")
    @GetMapping("/monitor")
    public ResponseEntity<AdminOpsMonitorDto> monitor() {
        assertAdminRole();
        return ResponseEntity.ok(adminOpsMonitorService.monitor());
    }
}

