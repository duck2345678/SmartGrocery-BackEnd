package com.smartgrocery.backend.controller;

// Force IDE re-scan - Updated at 11:18 AM
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.DataSanitizationService;
import com.smartgrocery.backend.service.ImageMigrationService;
import com.smartgrocery.backend.service.Neo4jCatalogRebuildService;
import com.smartgrocery.backend.service.SeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/utils")
@Tag(name = "Admin - Utilities", description = "API tiện ích cho Admin (Migration, etc.)")
public class AdminUtilityController {

    @Autowired
    private ImageMigrationService migrationService;

    @Autowired
    private DataSanitizationService sanitizationService;

    @Autowired
    private SeedService seedService;

    @Autowired
    private Neo4jCatalogRebuildService neo4jCatalogRebuildService;

    @Operation(summary = "Kích hoạt nạp lại dữ liệu danh mục sản phẩm (Seeding)")
    @PostMapping("/trigger-seed")
    public ResponseEntity<Map<String, String>> triggerSeed() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
        
        seedService.seedData();

        return ResponseEntity.ok(Map.of("message", "Quá trình nạp dữ liệu (Seeding) đã hoàn tất thành công. Kho hàng mới đã sẵn sàng!"));
    }

    @Operation(summary = "Di chuyển toàn bộ ảnh từ local sang Supabase Storage")
    @PostMapping("/migrate-images")
    public ResponseEntity<Map<String, String>> migrateImages() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
        
        new Thread(() -> {
            try {
                migrationService.migrateAll();
            } catch (Exception e) {
                // log error
            }
        }).start();

        return ResponseEntity.ok(Map.of("message", "Quá trình di chuyển ảnh đã bắt đầu ngầm. Vui lòng kiểm tra log để biết kết quả."));
    }

    @Operation(summary = "Rà soát sửa lỗi sản phẩm, xóa dữ liệu Nhà cung cấp & Kệ hàng")
    @PostMapping("/sanitize-data")
    public ResponseEntity<Map<String, String>> sanitizeData() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
        
        sanitizationService.sanitizeAndClear();

        return ResponseEntity.ok(Map.of("message", "Đã rà soát xong dữ liệu sản phẩm, xóa thông tin nhà cung cấp và vị trí kệ hàng thành công."));
    }
    @Operation(summary = "Kiem tra do lech giua PostgreSQL catalog va Neo4j graph")
    @GetMapping("/neo4j-catalog/audit")
    public ResponseEntity<Map<String, Object>> auditNeo4jCatalog() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }

        return ResponseEntity.ok(neo4jCatalogRebuildService.auditCatalogGraph());
    }

    @Operation(summary = "Xoa va tao lai toan bo Neo4j catalog graph tu du lieu san pham hien tai")
    @PostMapping("/neo4j-catalog/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildNeo4jCatalog() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }

        return ResponseEntity.ok(neo4jCatalogRebuildService.rebuildCatalogGraph());
    }
}
