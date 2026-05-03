package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOpsMonitorDto {
    private List<AdminOpsOrderDto> stagnantOrders;
    private List<AdminOpsOrderDto> stalledStaffOrders;
}

