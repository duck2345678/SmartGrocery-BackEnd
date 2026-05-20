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
public class ChatResponseDto {
    private String reply;
    private boolean success;
    private List<ShoppingItem> shoppingItems;
    private Long sessionId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoppingItem {
        private Long productId;
        private Long variantId;
        private String name;
        private String imageUrl;
        private BigDecimal price;
        private String unit;
        private String role; // PRIMARY or SECONDARY
    }
}
