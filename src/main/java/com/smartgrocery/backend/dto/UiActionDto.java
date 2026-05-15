package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UiActionDto {
    private String type;
    private Long productId;
    private Integer quantity;
    private String reason;
    private Map<String, Object> params;
}
