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
public class AINudgeDto {
    private Long productId;
    private String name;
    private String image;
    private BigDecimal price;
    private String reason;
    private Double confidenceScore;
}

