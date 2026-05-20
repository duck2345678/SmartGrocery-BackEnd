package com.smartgrocery.backend.dto;

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
public class ChatSessionDetailResponseDto {
    private Long id;
    private String title;
    private String contextType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ChatMessageResponseDto> messages;
}
