package com.smartgrocery.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {
    @NotBlank(message = "Message cannot be blank")
    private String message;

    private Long sessionId; // null = tạo phiên mới, có giá trị = tiếp tục phiên cũ
}
