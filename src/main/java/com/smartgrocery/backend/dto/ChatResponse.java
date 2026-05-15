package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private Long sessionId;
    private String aiMessageId;
    private String reply;
    @Builder.Default
    private List<ProposedItemDto> proposedItems = new ArrayList<>();
    @Builder.Default
    private List<SmartSuggestionDto> smartSuggestions = new ArrayList<>();
    private String intentDetected;
    private Long messageId;
    private Integer interactionCount;
    private String replyStatus;
    @Builder.Default
    private List<Long> recommendedProductIds = new ArrayList<>();
    private List<Long> removeVariantIds;
    private Map<Long, String> removeReasons;
    private Map<Long, String> explanations;
    private Float trustScore;
    private String thoughtProcess;
    private IntentPredictionDto intentPrediction;
    private String expectationPrompt;
    private String fallbackReply;
    private String streamUrl;
    private List<UiActionDto> uiActions;
}
