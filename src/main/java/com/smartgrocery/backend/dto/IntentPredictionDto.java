package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentPredictionDto {
    private String detectedIntent;
    private Float confidence;
    private String message;
    private List<SmartSuggestionDto> smartSuggestions;
    private String bundleActionUi;
    private Map<String, Object> metadata;
}
