package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.SupplierDto;
import com.smartgrocery.backend.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/suppliers")
@Tag(name = "Admin - Supplier", description = "Quản lý Nhà cung cấp")
public class SupplierController {

    @Autowired
    private SupplierService supplierService;

    @Operation(summary = "Lấy danh sách nhà cung cấp")
    @GetMapping
    public ResponseEntity<List<SupplierDto>> getAll() {
        return ResponseEntity.ok(supplierService.getAllSuppliers());
    }

    @Operation(summary = "Tạo nhà cung cấp mới")
    @PostMapping
    public ResponseEntity<SupplierDto> create(@RequestBody SupplierDto dto) {
        return ResponseEntity.ok(supplierService.createSupplier(dto));
    }
}
