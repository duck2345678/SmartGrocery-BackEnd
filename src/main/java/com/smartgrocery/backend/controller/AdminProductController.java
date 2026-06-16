package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AdminProductDeleteRequest;
import com.smartgrocery.backend.dto.AdminProductStatusRequest;
import com.smartgrocery.backend.dto.AdminProductSummaryDto;
import com.smartgrocery.backend.dto.AdminProductUpsertRequest;
import com.smartgrocery.backend.dto.ProductDto;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.AdminProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/products")
@Tag(name = "Admin - Products", description = "Admin product management")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    private void assertAdmin() {
        if (!SecurityUtils.hasAnyRole("ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "List admin products with pagination, search and filters")
    @GetMapping
    public ResponseEntity<Page<ProductDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean discounted,
            Pageable pageable
    ) {
        assertAdmin();
        return ResponseEntity.ok(adminProductService.search(search, categoryId, status, discounted, pageable));
    }

    @Operation(summary = "Get product catalog summary counts")
    @GetMapping("/summary")
    public ResponseEntity<AdminProductSummaryDto> getSummary() {
        assertAdmin();
        return ResponseEntity.ok(adminProductService.getSummary());
    }

    @Operation(summary = "Bulk cleanup: activate hidden products and hard delete soft-deleted items")
    @PostMapping("/cleanup")
    public ResponseEntity<String> cleanup(@AuthenticationPrincipal User actor) {
        assertAdmin();
        return ResponseEntity.ok(adminProductService.cleanupProducts(actor));
    }

    @Operation(summary = "Create product with image and variants")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductDto> create(
            @AuthenticationPrincipal User actor,
            @Valid @ModelAttribute AdminProductUpsertRequest request
    ) {
        assertAdmin();
        return ResponseEntity.ok(adminProductService.create(actor, request));
    }

    @Operation(summary = "Update product with optional image and variants")
    @PutMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductDto> update(
            @AuthenticationPrincipal User actor,
            @PathVariable Long productId,
            @Valid @ModelAttribute AdminProductUpsertRequest request
    ) {
        assertAdmin();
        return ResponseEntity.ok(adminProductService.update(actor, productId, request));
    }

    @Operation(summary = "Upload product image")
    @PostMapping(value = "/{productId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductDto> uploadImage(
            @AuthenticationPrincipal User actor,
            @PathVariable Long productId,
            @RequestPart("image") MultipartFile image
    ) {
        assertAdmin();
        return ResponseEntity.ok(adminProductService.updateImage(actor, productId, image));
    }

    @Operation(summary = "Set product visible/hidden status")
    @PatchMapping("/{productId}/status")
    public ResponseEntity<ProductDto> setStatus(
            @AuthenticationPrincipal User actor,
            @PathVariable Long productId,
            @Valid @RequestBody AdminProductStatusRequest request
    ) {
        assertAdmin();
        return ResponseEntity.ok(adminProductService.setStatus(actor, productId, request.getStatus(), request.getReason()));
    }

    @Operation(summary = "Soft delete product")
    @DeleteMapping("/{productId}")
    public ResponseEntity<ProductDto> softDelete(
            @AuthenticationPrincipal User actor,
            @PathVariable Long productId,
            @RequestBody(required = false) AdminProductDeleteRequest request
    ) {
        assertAdmin();
        return ResponseEntity.ok(adminProductService.softDelete(actor, productId, request != null ? request.getReason() : null));
    }

    @Operation(summary = "Restore soft-deleted product")
    @PostMapping("/{productId}/restore")
    public ResponseEntity<ProductDto> restore(
            @AuthenticationPrincipal User actor,
            @PathVariable Long productId,
            @RequestBody(required = false) AdminProductDeleteRequest request
    ) {
        assertAdmin();
        return ResponseEntity.ok(adminProductService.restore(actor, productId, request != null ? request.getReason() : null));
    }

    @Operation(summary = "Export products to Excel")
    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal User actor,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean discounted
    ) {
        assertAdmin();
        byte[] bytes = adminProductService.exportExcel(actor, search, categoryId, status, discounted);
        String filename = "products-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
