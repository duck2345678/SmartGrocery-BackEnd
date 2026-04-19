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
public class StaffOrderDto {
    private Long id;
    private String orderNumber;
    private String status;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String addressLine;
    private Integer totalItems;
    private BigDecimal totalAmount;
    private Long assigneeId;
    private String assigneeName;
    private LocalDateTime leaseExpiresAt;
    private LocalDateTime createdAt;
}

