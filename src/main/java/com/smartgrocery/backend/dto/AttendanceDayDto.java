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
public class AttendanceDayDto {
    private LocalDate date;
    private String shiftType;
    private String dayStatus; // ON_TIME, LATE, ABSENT, SCHEDULED, OFF
    private List<AttendanceRecordDto> records;
}
