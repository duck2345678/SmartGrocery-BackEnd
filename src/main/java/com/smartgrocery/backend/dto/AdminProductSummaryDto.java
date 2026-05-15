package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProductSummaryDto {
    private long totalCount;
    private long activeCount;
    private long hiddenCount;
    private long deletedCount;
}
