package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryDto {
    private String type;
    private Long sessionId;
    private Long id;
    private String title;
    private String sender;
    private String role;
    private String message;
    private String content;
    private LocalDateTime timestamp;
    private String createdAt;
    private String intentDetected;
}
