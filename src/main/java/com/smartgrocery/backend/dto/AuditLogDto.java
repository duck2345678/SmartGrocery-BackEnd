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
public class AuditLogDto {
    private Long id;
    private Long actorId;
    private String actorName;
    private String actionType;
    private String entityType;
    private Long entityId;
    private String reason;
    private JsonNode beforeState;
    private JsonNode afterState;
    private LocalDateTime createdAt;
}

