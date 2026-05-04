package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffPerformanceSummaryDto {
    private LocalDate date;

    private LocalDate weekFrom;
    private LocalDate weekTo;
    private long weekCompletedCount;

    private LocalDate monthFrom;
    private LocalDate monthTo;
    private long monthCompletedCount;
}

