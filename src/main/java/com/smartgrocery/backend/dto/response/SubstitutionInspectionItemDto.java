package com.smartgrocery.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubstitutionInspectionItemDto {
    private Integer rank;
    private Long variantId;
    private Long productId;
    private String sku;
    private String name;
    private String family;
    private Double score;
    private Double familySimilarity;
    private Double nutritionDistance;
    private Double graphDistance;
    private Integer stock;
    private Integer sharedTags;
    private Double stockBonus;
    private Double familyBonus;
    private Double sharedTagBonus;
    private Double mismatchPenalty;
    private String reason;
    private List<String> factors;
}
