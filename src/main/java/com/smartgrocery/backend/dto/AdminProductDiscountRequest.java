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
public class AdminProductDiscountRequest {
    private List<Long> variantIds;
    private BigDecimal discountPercentage; // Nếu muốn giảm theo %
    private BigDecimal fixedDiscountAmount; // Nếu muốn giảm số tiền cố định
    private Boolean applyToNetPrice; // true: tính từ netPrice hiện tại, false: set cứng giá mới
    private BigDecimal newNetPrice; // Giá bán mới trực tiếp
    private java.time.LocalDateTime flashSaleEndsAt;
}
