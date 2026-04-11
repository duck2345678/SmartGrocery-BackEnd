package com.smartgrocery.backend.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryStockDto {
    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private Long variantId;
    private String variantName;
    private String productName;
    private Integer availableQuantity;
    private Integer reservedQuantity;
}
