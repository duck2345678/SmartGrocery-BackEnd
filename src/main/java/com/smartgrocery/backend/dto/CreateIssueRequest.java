package com.smartgrocery.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIssueRequest {
    private Long orderId;
    private Long orderItemId;
    private String issueType;
    private JsonNode details;
}
