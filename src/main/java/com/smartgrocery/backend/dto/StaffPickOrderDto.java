package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffPickOrderDto {
    private Long orderId;
    private String orderNumber;
    private String status;
    private Long assigneeId;
    private LocalDateTime leaseExpiresAt;
    private String packingPhotoUrl;
    private String deliveryPhotoUrl;
    private List<StaffPickItemDto> items;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String addressLine;
    private String paymentMethod;
    private java.math.BigDecimal subtotal;
    private java.math.BigDecimal totalAmount;
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
}
