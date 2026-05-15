package com.smartgrocery.backend.dto;

import lombok.Data;

@Data
public class AddToCartRequest {
    private Long variantId;
    private Integer quantity;
    private Boolean allowSubstitution = false;
}
