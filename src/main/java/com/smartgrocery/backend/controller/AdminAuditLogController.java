package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AuditLogDto;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Admin - Audit Logs", description = "Truy vết thao tác nhạy cảm")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditService auditService;

    private void assertAdminRole() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Tìm kiếm audit logs")
    @GetMapping
    public ResponseEntity<Page<AuditLogDto>> list(
            @RequestParam(value = "actorId", required = false) Long actorId,
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) Long entityId,
            @RequestParam(value = "fromAt", required = false) LocalDateTime fromAt,
            @RequestParam(value = "toAt", required = false) LocalDateTime toAt,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size
    ) {
        assertAdminRole();
        return ResponseEntity.ok(auditService.search(actorId, actionType, entityType, entityId, fromAt, toAt, page, size));
    }
}

