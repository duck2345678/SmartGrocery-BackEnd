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
public class ChatSessionResponseDto {
    private Long id;
    private String title;
    private String contextType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
