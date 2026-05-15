package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponsePayload {
    private String reply;
    private String intentDetected;
    private Float trustScore;
    private String thoughtProcess;
    private IntentPredictionDto intentPrediction;
    @Builder.Default
    private List<Long> recommendedProductIds = new ArrayList<>();
    @Builder.Default
    private List<ProposedItemDto> proposedItems = new ArrayList<>();
    @Builder.Default
    private List<SmartSuggestionDto> smartSuggestions = new ArrayList<>();
    @Builder.Default
    private List<Long> removeVariantIds = new ArrayList<>();
    @Builder.Default
    private Map<Long, String> removeReasons = new HashMap<>();
    @Builder.Default
    private Map<Long, String> explanations = new HashMap<>();
    private String bestCouponCode;
    private Double potentialSavings;
}
