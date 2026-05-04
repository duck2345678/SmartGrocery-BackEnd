package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftConfigDto {
    private String shiftType;
    private List<ShiftBlock> blocks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftBlock {
        private int blockNumber;
        private LocalTime startTime;
        private LocalTime endTime;
    }
}
