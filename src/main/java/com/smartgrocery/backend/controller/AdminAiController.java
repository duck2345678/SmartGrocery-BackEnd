package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.Neo4jHealthDto;
import com.smartgrocery.backend.dto.Neo4jSyncDto;
import com.smartgrocery.backend.dto.TopQueryDto;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AdminAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/admin/ai")
@Tag(name = "Admin - AI", description = "AI engine health & analytics")
@RequiredArgsConstructor
public class AdminAiController {

    private final AdminAiService adminAiService;

    private void assertAdminRole() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Neo4j health check")
    @GetMapping("/neo4j-health")
    public ResponseEntity<Neo4jHealthDto> neo4jHealth() {
        assertAdminRole();
        return ResponseEntity.ok(adminAiService.neo4jHealth());
    }

    @Operation(summary = "Force sync Product nodes to Neo4j")
    @PostMapping("/neo4j-sync")
    public ResponseEntity<Neo4jSyncDto> neo4jSync() {
        assertAdminRole();
        return ResponseEntity.ok(adminAiService.neo4jSync());
    }

    @Operation(summary = "Top user queries in AI chat")
    @GetMapping("/chat-analytics/top-queries")
    public ResponseEntity<List<TopQueryDto>> topQueries(
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        assertAdminRole();
        return ResponseEntity.ok(adminAiService.topQueries(limit));
    }
}

