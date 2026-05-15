package com.smartgrocery.backend.service.recommendation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodFamilyProfile {
    private Long productId;
    private Long variantId;
    private String sku;
    private String productName;
    private String categoryName;
    private FoodFamilyGroup primaryFamily;

    @Builder.Default
    private Set<FoodFamilyGroup> familyTags = new LinkedHashSet<>();

    private BigDecimal caloriesPer100g;
    private BigDecimal proteinPer100g;
    private BigDecimal carbsPer100g;
    private BigDecimal fatPer100g;
    private BigDecimal fiberPer100g;
    private BigDecimal sugarPer100g;
    private BigDecimal sodiumMgPer100g;
    private Integer availableStock;
    private String rationale;

    public boolean matchesFamily(FoodFamilyGroup familyGroup) {
        if (familyGroup == null) return false;
        return primaryFamily == familyGroup || familyTags.contains(familyGroup);
    }
}
