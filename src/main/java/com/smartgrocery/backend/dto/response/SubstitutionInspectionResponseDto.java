package com.smartgrocery.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubstitutionInspectionResponseDto {
    private String sourceSku;
    private Long sourceVariantId;
    private Long sourceProductId;
    private String sourceName;
    private Integer limit;
    private String dietaryPreference;
    private String allergies;
    private LocalDateTime generatedAt;
    private List<SubstitutionInspectionItemDto> items;
}
