package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffPerformanceDailyDto {
    private LocalDate date;
    private long completedCount;
    private List<StaffPerformanceOrderDto> orders;
}

