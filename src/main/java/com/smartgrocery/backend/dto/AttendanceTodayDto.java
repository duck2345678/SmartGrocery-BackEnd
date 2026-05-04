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
public class AttendanceTodayDto {
    private LocalDate date;
    private String shiftType;
    private int currentBlock;
    private String nextAction;
    private List<AttendanceRecordDto> records;
}

