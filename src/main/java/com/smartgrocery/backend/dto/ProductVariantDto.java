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
public class ProductVariantDto {
    private Long id;
    private String sku;
    private String barcode;
    private String variantName;
    private String color;
    private String size;
    private String unit;
    private String packageSize;
    private Integer weightGram;
    private BigDecimal netPrice;
    private BigDecimal compareAtPrice;
    private BigDecimal vatPercent;
    private String status;
    private Integer stock;
}
