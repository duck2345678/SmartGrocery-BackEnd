package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    private Long id;
    private Long userId;
    private Long addressId;
    private String receiverName;
    private String receiverPhone;
    private String addressLine;
    private String orderNumber;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private String status;
    private String paymentMethod;
    private String paymentStatus;
    private String customerNote;
    private Long assigneeId;
    private LocalDateTime leaseExpiresAt;
    private String packingPhotoUrl;
    private String deliveryPhotoUrl;
    private LocalDateTime assignedAt;
    private LocalDateTime pickedAt;
    private LocalDateTime deliveredAt;
    private List<OrderItemDto> items;
    private Boolean aiGenerated;
    private String aiListCode;
    private String aiListName;
    private VoucherDto rewardVoucher;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
