package com.smartgrocery.backend.controller.admin;

import com.smartgrocery.backend.service.ai.CatalogSyncAdminService;
import com.smartgrocery.backend.service.ai.CanonicalSyncOrchestratorService;
import com.smartgrocery.backend.service.ai.AcceptanceCaseAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/catalog-sync")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogSyncController {

    private final CatalogSyncAdminService adminService;
    private final CanonicalSyncOrchestratorService canonicalSyncOrchestratorService;
    private final AcceptanceCaseAuditService acceptanceCaseAuditService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(adminService.getQueueStats());
    }

    @PostMapping("/requeue-dead")
    public ResponseEntity<Map<String, Object>> requeueDead() {
        int updated = adminService.requeueDeadEvents();
        return ResponseEntity.ok(Map.of("requeued", updated));
    }

    @PostMapping("/full-sync-checksum")
    public ResponseEntity<Map<String, Object>> fullSyncChecksum() {
        return ResponseEntity.ok(canonicalSyncOrchestratorService.runFullSyncAndChecksum());
    }

    @PostMapping("/acceptance-audit")
    public ResponseEntity<Map<String, Object>> acceptanceAudit() {
        return ResponseEntity.ok(acceptanceCaseAuditService.runAcceptanceCases());
    }
}
