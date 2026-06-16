package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.WarehouseDto;
import com.smartgrocery.backend.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/warehouses")
@Tag(name = "Admin - Warehouse", description = "Quản lý Kho bãi")
public class WarehouseController {

    @Autowired
    private WarehouseService warehouseService;

    @Operation(summary = "Lấy danh sách kho")
    @GetMapping
    public ResponseEntity<List<WarehouseDto>> getAll() {
        return ResponseEntity.ok(warehouseService.getAll());
    }

    @Operation(summary = "Tạo kho mới")
    @PostMapping
    public ResponseEntity<WarehouseDto> create(@RequestBody WarehouseDto dto) {
        return ResponseEntity.ok(warehouseService.create(dto));
    }
}
