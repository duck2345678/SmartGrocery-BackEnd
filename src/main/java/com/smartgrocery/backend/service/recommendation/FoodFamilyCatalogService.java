package com.smartgrocery.backend.service.recommendation;

import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.VariantNutritionFact;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.VariantNutritionFactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FoodFamilyCatalogService {

    private final ProductVariantRepository productVariantRepository;
    private final VariantNutritionFactRepository variantNutritionFactRepository;
    private final InventoryStockRepository inventoryStockRepository;

    public Map<Long, FoodFamilyProfile> buildCatalog() {
        List<ProductVariant> variants = productVariantRepository.findAll();
        List<Long> productIds = variants.stream()
                .map(variant -> variant.getProduct() != null ? variant.getProduct().getId() : null)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();

        Map<Long, VariantNutritionFact> nutritionByVariantId = variantNutritionFactRepository.findByProductIds(productIds).stream()
                .filter(nutrition -> nutrition.getVariant() != null && nutrition.getVariant().getId() != null)
                .collect(Collectors.toMap(nutrition -> nutrition.getVariant().getId(), nutrition -> nutrition, (left, right) -> left, LinkedHashMap::new));

        Map<Long, Integer> stockByVariantId = inventoryStockRepository.sumAvailableByVariantIds(variantIds).stream()
            .collect(Collectors.toMap(
                InventoryStockRepository.VariantStockSum::getVariantId,
                row -> row.getTotalAvailable() == null ? 0 : row.getTotalAvailable().intValue(),
                (a, b) -> a + b,
                LinkedHashMap::new));

        Map<Long, FoodFamilyProfile> catalog = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            VariantNutritionFact nutrition = nutritionByVariantId.get(variant.getId());
            FoodFamilyProfile profile = FoodFamilyRules.inferProfile(variant, nutrition);
            profile.setAvailableStock(stockByVariantId.getOrDefault(variant.getId(), 0));
            catalog.put(variant.getId(), profile);
        }
        return catalog;
    }

    public Optional<FoodFamilyProfile> resolveVariant(Long variantId) {
        if (variantId == null) {
            return Optional.empty();
        }
        return productVariantRepository.findById(variantId)
                .map(variant -> {
                    VariantNutritionFact nutrition = variantNutritionFactRepository.findByProductIds(List.of(variant.getProduct().getId())).stream()
                            .filter(item -> item.getVariant() != null && variantId.equals(item.getVariant().getId()))
                            .findFirst()
                            .orElse(null);
                    FoodFamilyProfile profile = FoodFamilyRules.inferProfile(variant, nutrition);
                    profile.setAvailableStock(inventoryStockRepository.findByVariantId(variantId).map(stock -> stock.getAvailableQuantity() == null ? 0 : stock.getAvailableQuantity()).orElse(0));
                    return profile;
                });
    }
}
