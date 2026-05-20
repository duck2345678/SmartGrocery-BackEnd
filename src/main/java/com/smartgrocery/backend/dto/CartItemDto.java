package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDto {
    private Long id;
    private Long productId;
    private Long variantId;
    private String variantName;
    private String unit;
    private String productName;
    private String sku;
    private BigDecimal unitPrice;
    private BigDecimal compareAtPrice;
    private Integer quantity;
    private BigDecimal subtotal;
    private String imageUrl;
    private Integer stock;
    private LocalDateTime addedAt;
    private Boolean allowSubstitution;
    private String source;
    private String aiListCode;
    private String aiListName;
}
