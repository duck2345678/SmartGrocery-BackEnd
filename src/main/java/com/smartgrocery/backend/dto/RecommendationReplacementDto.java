package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationReplacementDto {
    private Long variantId;
    private Long productId;
    private String sku;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private String family;
    private List<String> matchedFamilies;
    private double graphDistance;
    private double nutritionDistance;
    private String reason;
    private Boolean recommended;
}
