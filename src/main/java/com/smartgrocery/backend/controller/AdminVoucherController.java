package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AdminProductDiscountRequest;
import com.smartgrocery.backend.dto.VoucherDto;
import com.smartgrocery.backend.dto.VoucherGenerationRequest;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.service.AdminProductService;
import com.smartgrocery.backend.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/admin/vouchers")
@Tag(name = "Admin - Voucher", description = "Quản lý Voucher & Khuyến mãi")
@RequiredArgsConstructor
public class AdminVoucherController {

    private final VoucherService voucherService;
    private final AdminProductService adminProductService;

    @Operation(summary = "Lấy toàn bộ danh sách voucher")
    @GetMapping
    public ResponseEntity<List<VoucherDto>> getAllVouchers() {
        return ResponseEntity.ok(voucherService.getAllVouchers());
    }

    @Operation(summary = "Thiết lập giảm giá sản phẩm hàng loạt")
    @PostMapping("/discounts")
    public ResponseEntity<Integer> updateDiscounts(@RequestAttribute("actor") User actor, @RequestBody AdminProductDiscountRequest request) {
        return ResponseEntity.ok(adminProductService.updateDiscounts(actor, request));
    }

    @Operation(summary = "Tạo voucher hàng loạt tự động")
    @PostMapping("/generate")
    public ResponseEntity<List<VoucherDto>> generateVouchers(@RequestBody VoucherGenerationRequest request) {
        return ResponseEntity.ok(voucherService.generateVouchers(request));
    }

    @Operation(summary = "Xóa voucher")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVoucher(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return ResponseEntity.noContent().build();
    }
}
