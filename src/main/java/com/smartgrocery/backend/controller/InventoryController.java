package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.InventoryStockDto;
import com.smartgrocery.backend.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/admin/inventory")
@Tag(name = "Admin - Inventory", description = "Quản lý Tồn kho")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @Operation(summary = "Lấy tất cả tồn kho")
    @GetMapping
    public ResponseEntity<List<InventoryStockDto>> getAll() {
        return ResponseEntity.ok(inventoryService.getAll());
    }

    @Operation(summary = "Lấy tồn kho theo kho bãi")
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<InventoryStockDto>> getByWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(inventoryService.getByWarehouse(warehouseId));
    }
}
