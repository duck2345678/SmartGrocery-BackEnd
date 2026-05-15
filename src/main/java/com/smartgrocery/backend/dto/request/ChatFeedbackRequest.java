package com.smartgrocery.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatFeedbackRequest {
    @NotNull(message = "Message ID is required")
    private Long messageId;

    @NotBlank(message = "Feedback type is required")
    private String feedbackType; // HELPFUL, NOT_HELPFUL

    private String comment; // Optional additional feedback
}
