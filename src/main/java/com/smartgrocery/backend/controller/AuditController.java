package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AuditLogDto;
import com.smartgrocery.backend.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/admin/audit")
@Tag(name = "Admin - Audit", description = "Giám sát hệ thống (Audit Logs)")
public class AuditController {

    @Autowired
    private AuditService auditService;

    @Operation(summary = "Lấy toàn bộ nhật ký hệ thống")
    @GetMapping("/logs")
    public ResponseEntity<List<AuditLogDto>> getAllLogs() {
        return ResponseEntity.ok(auditService.getAllLogs());
    }
}
