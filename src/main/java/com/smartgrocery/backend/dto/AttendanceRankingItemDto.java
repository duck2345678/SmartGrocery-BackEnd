package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRankingItemDto {
    private Long userId;
    private String userFullName;
    private int lateCount;
    private int absentCount;
    private int earlyCount;
    private int attendedDays;
    private int score;
}
