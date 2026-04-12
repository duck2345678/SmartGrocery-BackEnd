package com.smartgrocery.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditLogDto {
    private Long id;
    private Long actorUserId;
    private String actorName;
    private String actionCode;
    private String targetType;
    private Long targetId;
    private String payloadJson;
    private LocalDateTime createdAt;
}
