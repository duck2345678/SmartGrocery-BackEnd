package com.smartgrocery.backend.dto;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PurchaseOrderItemDto {
    private Long id;
    private Long variantId;
    private String variantName;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
