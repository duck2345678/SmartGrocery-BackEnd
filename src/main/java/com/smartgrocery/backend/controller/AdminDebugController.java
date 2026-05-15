package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.repository.jpa.RoleRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/admin/debug")
@RequiredArgsConstructor
@Tag(name = "Admin - Debug", description = "Debug helpers for admin login and seed status")
public class AdminDebugController {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryStockRepository inventoryStockRepository;

    @Value("${app.seeding.enabled:true}")
    private boolean seedingEnabled;

    @Operation(summary = "Check admin seed status")
    @GetMapping("/admin-seed")
    public ResponseEntity<Map<String, Object>> adminSeedStatus() {
        boolean adminRoleExists = roleRepository.findByName("ADMIN").isPresent();
        Optional<com.smartgrocery.backend.entity.User> adminUser = userRepository.findByEmail("admin.p0@smartgrocery.com");
        Optional<com.smartgrocery.backend.entity.User> staffUser = userRepository.findByEmail("staff.p0@smartgrocery.com");
        Optional<com.smartgrocery.backend.entity.User> customerUser = userRepository.findByEmail("customer.p0@smartgrocery.com");
        String adminRoleName = adminUser.map(u -> u.getRole() != null ? u.getRole().getName() : null).orElse(null);
        boolean adminRoleOk = "ADMIN".equalsIgnoreCase(adminRoleName);

        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("seedingEnabled", seedingEnabled),
                Map.entry("adminRoleExists", adminRoleExists),
                Map.entry("adminUserEmail", "admin.p0@smartgrocery.com"),
                Map.entry("adminUserExists", adminUser.isPresent()),
                Map.entry("adminUserRole", adminRoleName),
                Map.entry("adminUserRoleOk", adminRoleOk),
                Map.entry("staffUserExists", staffUser.isPresent()),
                Map.entry("staffUserRole", staffUser.map(u -> u.getRole() != null ? u.getRole().getName() : null).orElse(null)),
                Map.entry("customerUserExists", customerUser.isPresent()),
                Map.entry("customerUserRole", customerUser.map(u -> u.getRole() != null ? u.getRole().getName() : null).orElse(null))
        ));
    }

    @Operation(summary = "Check product seed quality (count + null fields)")
    @GetMapping("/product-seed-health")
    public ResponseEntity<Map<String, Object>> productSeedHealth() {
        var products = productRepository.findAll();
        var variants = productVariantRepository.findAll();
        var stocks = inventoryStockRepository.findAll();

        var productsWithNull = products.stream()
                .filter(p -> p.getProductCode() == null || p.getName() == null || p.getCategory() == null || p.getDescription() == null
                        || p.getShortDescription() == null || p.getOriginCountry() == null || p.getImage() == null
                        || p.getStatus() == null || p.getIsFeatured() == null)
                .limit(50)
                .map(p -> Map.of(
                        "productCode", String.valueOf(p.getProductCode()),
                        "name", String.valueOf(p.getName())
                ))
                .toList();

        var variantsWithNull = variants.stream()
                .filter(v -> v.getProduct() == null || v.getSku() == null || v.getBarcode() == null || v.getVariantName() == null
                        || v.getUnit() == null || v.getPackageSize() == null || v.getWeightGram() == null
                        || v.getNetPrice() == null || v.getCompareAtPrice() == null || v.getCostPrice() == null
                        || v.getVatPercent() == null || v.getStatus() == null)
                .limit(50)
                .map(v -> Map.of(
                        "sku", String.valueOf(v.getSku()),
                        "productName", String.valueOf(v.getProduct() != null ? v.getProduct().getName() : null)
                ))
                .toList();

        var stocksWithNull = stocks.stream()
                .filter(s -> s.getWarehouse() == null || s.getVariant() == null || s.getAvailableQuantity() == null || s.getReservedQuantity() == null)
                .limit(50)
                .map(s -> Map.of("stockId", String.valueOf(s.getId())))
                .toList();

        return ResponseEntity.ok(Map.of(
                "productsCount", products.size(),
                "variantsCount", variants.size(),
                "stocksCount", stocks.size(),
                "productsWithNullCount", productsWithNull.size(),
                "variantsWithNullCount", variantsWithNull.size(),
                "stocksWithNullCount", stocksWithNull.size(),
                "productsWithNullSample", productsWithNull,
                "variantsWithNullSample", variantsWithNull,
                "stocksWithNullSample", stocksWithNull
        ));
    }
}
