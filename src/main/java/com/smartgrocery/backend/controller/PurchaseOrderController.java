package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.PurchaseOrderDto;
import com.smartgrocery.backend.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/purchase-orders")
@Tag(name = "Admin - Purchase Order", description = "Quản lý Nhập hàng")
public class PurchaseOrderController {

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Operation(summary = "Lấy danh sách đơn nhập hàng")
    @GetMapping
    public ResponseEntity<List<PurchaseOrderDto>> getAll() {
        return ResponseEntity.ok(purchaseOrderService.getAll());
    }

    @Operation(summary = "Tạo đơn nhập hàng nháp")
    @PostMapping
    public ResponseEntity<PurchaseOrderDto> create(@RequestBody PurchaseOrderDto dto) {
        return ResponseEntity.ok(purchaseOrderService.createOrder(dto));
    }

    @Operation(summary = "Xác nhận nhập kho cho đơn hàng")
    @PostMapping("/{poId}/receive")
    public ResponseEntity<PurchaseOrderDto> receive(@PathVariable Long poId, @RequestParam Long warehouseId) {
        try {
            return ResponseEntity.ok(purchaseOrderService.receiveOrder(poId, warehouseId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
