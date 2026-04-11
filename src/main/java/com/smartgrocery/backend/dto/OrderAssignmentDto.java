package com.smartgrocery.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderAssignmentDto {
    private Long id;
    private Long orderId;
    private String orderCode;
    private Long staffUserId;
    private String staffName;
    private String taskType;
    private String status;
    private String proofImageUrl;
    private LocalDateTime assignedAt;
    private LocalDateTime completedAt;

    // UI Helpers
    private String customerName;
    private String customerPhone;
    private String deliveryAddress;
    private Integer totalItems;
}
