package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffSubstitutionOptionDto {
    private Long variantId;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private Boolean isRecommended;
}

