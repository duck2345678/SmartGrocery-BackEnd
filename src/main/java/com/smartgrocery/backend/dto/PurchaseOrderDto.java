package com.smartgrocery.backend.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PurchaseOrderDto {
    private Long id;
    private String poNumber;
    private BigDecimal totalAmount;
    private String status;
    private List<PurchaseOrderItemDto> items;
    private LocalDateTime createdAt;
}
