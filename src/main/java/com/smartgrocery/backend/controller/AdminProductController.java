package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AdminProductUpsertRequest;
import com.smartgrocery.backend.dto.ProductDto;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AdminProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/admin/products")
@Tag(name = "Admin - Products", description = "API quản lý sản phẩm (Admin)")
public class AdminProductController {

    @Autowired
    private AdminProductService adminProductService;

    @Operation(summary = "Tạo sản phẩm mới (multipart: fields + image)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductDto> create(@ModelAttribute AdminProductUpsertRequest request) {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return ResponseEntity.ok(adminProductService.create(request));
    }

    @Operation(summary = "Cập nhật sản phẩm (multipart: fields + image optional)")
    @PutMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductDto> update(
            @PathVariable Long productId,
            @ModelAttribute AdminProductUpsertRequest request
    ) {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return ResponseEntity.ok(adminProductService.update(productId, request));
    }

    @Operation(summary = "Upload ảnh sản phẩm (jpg/png/webp, max 2MB)")
    @PostMapping(value = "/{productId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductDto> uploadImage(
            @PathVariable Long productId,
            @RequestPart("image") MultipartFile image
    ) {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return ResponseEntity.ok(adminProductService.updateImage(productId, image));
    }
}

