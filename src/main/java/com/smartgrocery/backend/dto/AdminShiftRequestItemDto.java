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
public class AdminShiftRequestItemDto {
    private Long id;
    private Long userId;
    private String userFullName;
    private LocalDate workDate;
    private String shiftType;
    private String selectedBlocks;
    private String status;
    private String adminNote;
    private long scheduledCount;
    private long scheduledAfterApprove;
    private LocalDateTime createdAt;
}

