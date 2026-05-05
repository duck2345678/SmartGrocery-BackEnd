package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceChartPointDto {
    private LocalDate date;
    private long workedMinutes;
    private long scheduledMinutes;
    private int completedBlocks;
    private int scheduledBlocks;
    private boolean late;
    private boolean early;
}
