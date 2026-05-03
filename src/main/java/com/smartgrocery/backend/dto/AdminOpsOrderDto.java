package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOpsOrderDto {
    private Long orderId;
    private String orderNumber;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long assigneeId;
    private String assigneeName;
    private LocalDateTime leaseExpiresAt;
    private Integer minutesToSla;
    private Integer minutesSinceUpdate;
}

