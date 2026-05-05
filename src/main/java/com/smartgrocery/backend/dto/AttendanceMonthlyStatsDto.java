package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceMonthlyStatsDto {
    private int year;
    private int month;
    private LocalDate startDate;
    private LocalDate endDate;
    private int scheduledDays;
    private int attendedDays;
    private int absentDays;
    private int lateCheckIns;
    private int earlyCheckOuts;
    private int onTimeCheckIns;
    private int onTimeCheckOuts;
    private int totalBlocks;
    private int completedBlocks;
    private double completionRate;
    private long totalWorkedMinutes;
    private long totalScheduledMinutes;
    private long lateMinutes;
    private long earlyMinutes;
    private List<AttendanceDailySummaryDto> dailySummaries;
    private List<AttendanceChartPointDto> chartPoints;
}
