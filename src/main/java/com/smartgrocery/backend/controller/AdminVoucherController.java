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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/admin/vouchers", "/api/v1/admin/vouchers"})
@Tag(name = "Admin - Voucher", description = "Quan ly Voucher va Khuyen mai")
@RequiredArgsConstructor
public class AdminVoucherController {

    private final VoucherService voucherService;
    private final AdminProductService adminProductService;

    @Operation(summary = "Lay toan bo danh sach voucher")
    @GetMapping
    public ResponseEntity<List<VoucherDto>> getAllVouchers() {
        return ResponseEntity.ok(voucherService.getAllVouchers());
    }

    @Operation(summary = "Thiet lap giam gia san pham hang loat")
    @PostMapping("/discounts")
    public ResponseEntity<Integer> updateDiscounts(
            @AuthenticationPrincipal User actor,
            @RequestBody AdminProductDiscountRequest request
    ) {
        return ResponseEntity.ok(adminProductService.updateDiscounts(actor, request));
    }

    @Operation(summary = "Tao voucher hang loat tu dong")
    @PostMapping("/generate")
    public ResponseEntity<List<VoucherDto>> generateVouchers(@RequestBody VoucherGenerationRequest request) {
        return ResponseEntity.ok(voucherService.generateVouchers(request));
    }

    @Operation(summary = "Xoa voucher")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVoucher(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return ResponseEntity.noContent().build();
    }
}
