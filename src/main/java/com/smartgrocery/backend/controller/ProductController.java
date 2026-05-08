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

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product", description = "Quáº£n lÃ½ sáº£n pháº©m (Catalog Core)")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Operation(summary = "Láº¥y danh sÃ¡ch sáº£n pháº©m (cÃ³ phÃ¢n trang)")
    @GetMapping
    public ResponseEntity<Page<ProductDto>> getAllProducts(Pageable pageable) {
        return ResponseEntity.ok(productService.getAllProducts(pageable));
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

    @Operation(summary = "Tìm kiếm sản phẩm theo tên hoặc danh mục")
    @GetMapping("/search")
    public ResponseEntity<Page<ProductDto>> searchProducts(
            @RequestParam String query,
            Pageable pageable
    ) {
        return ResponseEntity.ok(productService.searchProducts(query, pageable));
    }
}
