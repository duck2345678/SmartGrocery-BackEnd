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
public class AssignOrderResponse {
    private Long orderId;
    private Long assigneeId;
    private String status;
    private LocalDateTime leaseExpiresAt;
}

