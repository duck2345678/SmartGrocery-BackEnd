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
public class VoucherGenerationRequest {
    private int quantity;
    private String prefix; // e.g., "SG"
    private String discountType; // "PERCENTAGE" or "FIXED_AMOUNT"
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Integer usageLimitPerVoucher;
    private String description;
    private Boolean hidden;
    private String revealTrigger;
}
