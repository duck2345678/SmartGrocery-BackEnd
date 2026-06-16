package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.ProductDto;
import com.smartgrocery.backend.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product", description = "Quản lý sản phẩm (Catalog Core)")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Operation(summary = "Lấy danh sách sản phẩm (có phân trang, tìm kiếm, lọc theo category)")
    @GetMapping
    public ResponseEntity<Page<ProductDto>> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            Pageable pageable) {
        return ResponseEntity.ok(productService.getProducts(search, categoryId, pageable));
    }

    @Operation(summary = "Láº¥y chi tiáº¿t sáº£n pháº©m theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(productService.getProductById(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Láº¥y chi tiáº¿t sáº£n pháº©m theo Product Code")
    @GetMapping("/code/{productCode}")
    public ResponseEntity<ProductDto> getProductByCode(@PathVariable String productCode) {
        try {
            return ResponseEntity.ok(productService.getProductByCode(productCode));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
