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
    private List<StaffPickItemDto> items;
}

