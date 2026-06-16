package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherDto {
    private Long id;
    private String voucherCode;
    private String description;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private LocalDateTime validUntil;
    private Boolean active;
    private Boolean hidden;
    private String revealTrigger;
    private Long assignedUserId;
    private Long unlockedByOrderId;
    private Integer usageLimitPerVoucher;
    private Integer claimCount;
    private Integer minAge;
    private Integer maxAge;
    private Integer usedCount;
    private String status;
    private LocalDateTime claimedAt;
    private Boolean claimed;
    private Boolean used;
    private LocalDateTime usedAt;
    private String claimStatus;
    private LocalDateTime claimExpiresAt;
}
