package com.smartgrocery.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatFeedbackResponse {
    private String message;
    private Float updatedSatisfactionScore;
    private String nextBehaviorMode; // "ASK_MORE", "PROACTIVE", "NORMAL"
}
