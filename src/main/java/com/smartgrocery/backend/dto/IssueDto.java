package com.smartgrocery.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueDto {
    private Long id;
    private Long orderId;
    private Long orderItemId;
    private Long reporterId;
    private String reporterName;
    private String issueType;
    private String status;
    private JsonNode details;
    private LocalDateTime createdAt;
}
