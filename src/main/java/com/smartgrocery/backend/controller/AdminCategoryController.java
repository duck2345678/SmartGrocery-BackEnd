package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AdminCategoryUpsertRequest;
import com.smartgrocery.backend.entity.Category;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AdminCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/admin/categories")
@Tag(name = "Admin - Categories", description = "Quản lý danh mục (Admin)")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    private void assertAdminRole() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Danh sách categories")
    @GetMapping
    public ResponseEntity<List<Category>> list() {
        assertAdminRole();
        return ResponseEntity.ok(adminCategoryService.listAll());
    }

    @Operation(summary = "Tạo category")
    @PostMapping
    public ResponseEntity<Category> create(@RequestBody AdminCategoryUpsertRequest request) {
        assertAdminRole();
        return ResponseEntity.ok(adminCategoryService.create(request));
    }

    @Operation(summary = "Cập nhật category")
    @PutMapping("/{id}")
    public ResponseEntity<Category> update(@PathVariable("id") Long id, @RequestBody AdminCategoryUpsertRequest request) {
        assertAdminRole();
        return ResponseEntity.ok(adminCategoryService.update(id, request));
    }

    @Operation(summary = "Deactivate category (soft delete)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Category> deactivate(@PathVariable("id") Long id) {
        assertAdminRole();
        return ResponseEntity.ok(adminCategoryService.deactivate(id));
    }
}

