package com.smartgrocery.backend.dto.response;

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
public class AiChatResponse {
    private Long sessionId;
    private String aiMessageId;
    private String reply;
    private List<Long> recommendedProductIds;
    private List<ProposedItem> proposedItems;
    private List<Long> removeVariantIds;
    private Map<Long, String> removeReasons; // variantId -> reason (why this item should be removed)
    private Long rewardVoucherId; // ID of voucher given to user, if any
    private Map<Long, String> explanations; // productId -> explanation (why this product is recommended)
    private Integer trustScore; // 0-100: confidence in recommendations (for transparency)
    private String thoughtProcess; // Chain of Thought: AI's internal reasoning
    private String expectationPrompt; // quick follow-up question to confirm expectation

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProposedItem {
        private Long variantId;
        private Integer quantity;
        private String note;
        private String reason; // Explainable AI: why this product was suggested
        private Long substitutionFor; // If this is a replacement for another product
    }
}
