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
public class AttendanceDailySummaryDto {
    private LocalDate date;
    private String shiftType;
    private String dayStatus;
    private int scheduledBlocks;
    private int completedBlocks;
    private boolean hasLateCheckIn;
    private boolean hasEarlyCheckOut;
    private long workedMinutes;
    private long scheduledMinutes;
}
