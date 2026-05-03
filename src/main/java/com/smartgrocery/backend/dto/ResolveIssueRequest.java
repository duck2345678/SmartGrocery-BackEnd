package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveIssueRequest {
    private String resolutionType; // SUBSTITUTE, PARTIAL, CANCEL_LINE, CANCEL_ORDER
    private Boolean releaseOrder;
    private String resolutionNotes;
    private Long substituteProductId;
    private Integer partialQuantity;
}
