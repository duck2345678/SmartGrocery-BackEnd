package com.smartgrocery.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ShipmentDto {
    private Long id;
    private Long orderId;
    private String orderCode;
    private String carrierName;
    private String trackingCode;
    private String shipmentStatus;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
}
