package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private Long id;
    private String productCode;
    private String name;
    private String shortDescription;
    private String description;
    private String image;
    private String originCountry;
    private String status;
    private Boolean isFeatured;
    private CategoryDto category;
    private BrandDto brand;
    private List<ProductVariantDto> variants;
    private Long purchaseCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
