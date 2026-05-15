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
public class StaffPickItemDto {
    private Long id;
    private Long orderItemId;
    private Long variantId;
    private String sku;
    private String barcode;
    private String name;
    private String productName;
    private String variantName;
    private Integer quantity;
    private Integer orderedQuantity;
    private Integer pickedQuantity;
    private BigDecimal price;
    private BigDecimal unitPrice;
    private String imageUrl;
    private Integer stockQuantity;
}
