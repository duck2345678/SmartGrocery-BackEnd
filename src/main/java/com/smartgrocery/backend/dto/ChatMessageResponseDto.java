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
public class ChatMessageResponseDto {
    private Long id;
    private String role;
    private String content;
    private List<ChatResponseDto.ShoppingItem> shoppingItems;
    private Long latencyMs;
    private LocalDateTime createdAt;
}
