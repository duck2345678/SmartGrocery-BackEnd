package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceInsightDto {
    private List<AttendanceChartPointDto> chartPoints;
    private List<AttendanceRankingItemDto> lateRanking;
    private List<AttendanceRankingItemDto> absentRanking;
    private List<AttendanceRankingItemDto> earlyRanking;
    private List<AttendanceDailySummaryDto> topLateDays;
    private List<AttendanceDailySummaryDto> topEarlyDays;
}
