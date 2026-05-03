package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCategoryUpsertRequest {
    private String categoryCode;
    private String name;
    private String description;
    private Integer sortOrder;
    private Boolean isActive;
    private Long parentCategoryId;
}

