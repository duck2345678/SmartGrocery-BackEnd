package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecordDto {
    private Long id;
    private LocalDate workDate;
    private String shiftType;
    private Integer blockNumber;
    private LocalDateTime checkInAt;
    private LocalDateTime checkOutAt;
    private String checkInStatus;
    private String checkOutStatus;
    private String note;
}
