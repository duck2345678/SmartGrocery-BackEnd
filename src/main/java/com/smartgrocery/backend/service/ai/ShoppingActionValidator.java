package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShoppingActionValidator {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryStockRepository inventoryStockRepository;

    public Set<Long> findActiveStockedProductIds(Collection<Long> productIds) {
        List<Long> ids = productIds == null ? List.of() : productIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Set.of();
        }

        try {
            Set<Long> activeProductIds = productRepository.findAllById(ids).stream()
                    .filter(product -> "ACTIVE".equalsIgnoreCase(product.getStatus()))
                    .map(Product::getId)
                    .collect(Collectors.toSet());
            if (activeProductIds.isEmpty()) {
                return Set.of();
            }

            List<ProductVariant> variants = productVariantRepository.findByProductIdsAndStatusWithProduct(
                            new ArrayList<>(activeProductIds),
                            "ACTIVE"
                    ).stream()
                    .filter(this::isActiveVariantForActiveProduct)
                    .toList();
            if (variants.isEmpty()) {
                return Set.of();
            }

            List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();
            Map<Long, Long> stockByVariantId = inventoryStockRepository.sumAvailableByVariantIds(variantIds).stream()
                    .collect(Collectors.toMap(
                            InventoryStockRepository.VariantStockSum::getVariantId,
                            InventoryStockRepository.VariantStockSum::getTotalAvailable
                    ));

            Map<Long, Long> stockByProductId = new HashMap<>();
            for (ProductVariant variant : variants) {
                long stock = stockByVariantId.getOrDefault(variant.getId(), 0L);
                Long productId = variant.getProduct().getId();
                stockByProductId.put(productId, stockByProductId.getOrDefault(productId, 0L) + stock);
            }

            return stockByProductId.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Batch stock guard failed: {}", e.getMessage());
            return Set.of();
        }
    }

    private boolean isActiveVariantForActiveProduct(ProductVariant variant) {
        return variant != null
                && "ACTIVE".equalsIgnoreCase(variant.getStatus())
                && variant.getProduct() != null
                && "ACTIVE".equalsIgnoreCase(variant.getProduct().getStatus());
    }
}
