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
    private Long orderItemId;
    private Long variantId;
    private String sku;
    private String barcode;
    private String productName;
    private String variantName;
    private String aisleLocation;
    private Integer orderedQuantity;
    private Integer pickedQuantity;
    private Boolean allowSubstitution;
    private BigDecimal unitPrice;
}
