package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AuditLogDto;
import com.smartgrocery.backend.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/audit")
@Tag(name = "Admin - Audit", description = "Giám sát hệ thống (Audit Logs)")
public class AuditController {

    @Autowired
    private AuditService auditService;

    @Operation(summary = "Tìm kiếm audit logs")
    @GetMapping("/logs")
    public ResponseEntity<Page<AuditLogDto>> search(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) LocalDateTime fromAt,
            @RequestParam(required = false) LocalDateTime toAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(auditService.search(actorId, actionType, entityType, entityId, fromAt, toAt, page, size));
    }
}
