package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.InventoryStockDto;
import com.smartgrocery.backend.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inventory")
@Tag(name = "Admin - Inventory", description = "Quan ly ton kho")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @Operation(summary = "Lay danh sach ton kho phan trang")
    @GetMapping
    public ResponseEntity<Page<InventoryStockDto>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(inventoryService.getAll(page, size, search));
    }

    @Operation(summary = "Lay ton kho theo kho bai phan trang")
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<Page<InventoryStockDto>> getByWarehouse(
            @PathVariable Long warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(inventoryService.getByWarehouse(warehouseId, page, size, search));
    }
}
