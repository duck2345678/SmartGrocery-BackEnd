package com.smartgrocery.backend.dto;

import lombok.Data;

@Data
public class UpdateCartItemRequest {
    private Integer quantity;
    private Boolean allowSubstitution;
}
