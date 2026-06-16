package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.ShipmentDto;
import com.smartgrocery.backend.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shipments")
@Tag(name = "Admin - Shipment", description = "Quản lý Giao hàng")
public class ShipmentController {

    @Autowired
    private ShipmentService shipmentService;

    @Operation(summary = "Lấy danh sách lô hàng")
    @GetMapping
    public ResponseEntity<List<ShipmentDto>> getAll() {
        return ResponseEntity.ok(shipmentService.getAll());
    }

    @Operation(summary = "Tạo lô hàng mới cho Order")
    @PostMapping
    public ResponseEntity<ShipmentDto> create(@RequestBody ShipmentDto dto) {
        return ResponseEntity.ok(shipmentService.createShipment(dto));
    }

    @Operation(summary = "Cập nhật trạng thái giao hàng")
    @PutMapping("/{shipmentId}/status")
    public ResponseEntity<ShipmentDto> updateStatus(@PathVariable Long shipmentId, @RequestParam String status) {
        return ResponseEntity.ok(shipmentService.updateStatus(shipmentId, status));
    }
}
