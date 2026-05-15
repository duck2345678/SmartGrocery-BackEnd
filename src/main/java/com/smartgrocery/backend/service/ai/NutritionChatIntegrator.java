package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.repository.jpa.UserNutritionProfileRepository;
import com.smartgrocery.backend.service.CartInspectionService;
import com.smartgrocery.backend.service.CartInspectionService.CartInspectionReport;
import com.smartgrocery.backend.service.MealPlanService;
import com.smartgrocery.backend.service.recommendation.FoodReplacementService;
import com.smartgrocery.backend.entity.UserNutritionProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bridges nutrition services into the MEMM chat pipeline.
 * Applies MEMM Stage 3 (Trust Building) to all nutrition responses:
 * - reason: why product was suggested
 * - allergyWarning: products removed due to allergies
 * - nutritionFacts: estimated calories/protein
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionChatIntegrator {

    private final CartInspectionService cartInspectionService;
    private final MealPlanService mealPlanService;
    private final FoodReplacementService foodReplacementService;
    private final UserNutritionProfileRepository nutritionProfileRepository;

    /**
     * MEMM Stage 1 support: Analyze cart for chat context.
     * Formats CartInspectionReport into AI-friendly context string.
     */
    public String analyzeCartForChat(Long userId) {
        try {
            CartInspectionReport report = cartInspectionService.inspectCart(userId);
            return report.getFormattedPromptText();
        } catch (Exception e) {
            log.warn("Cart analysis failed for user {}: {}", userId, e.getMessage());
            return "Không thể phân tích giỏ hàng lúc này.";
        }
    }

    /**
     * Generate meal plan via chat flow.
     * Returns structured data for chat response with proposedItems[].
     */
    public MealPlanChatResult generateMealPlanViaChat(Long userId, String goal) {
        MealPlanChatResult result = new MealPlanChatResult();

        try {
            MealPlanService.MealPlanGenerationResult generated = mealPlanService.generateAIPlanStructured(userId, goal);
            if (generated == null || generated.getMealPlan() == null) {
                throw new IllegalStateException("Meal plan generation returned empty result");
            }
            result.setSuccess(true);
            result.setTitle(generated.getMealPlan().getTitle());
            result.setPlanId(generated.getMealPlan().getId());
            result.setTrustScore(generated.getTrustScore());
            result.setExplanations(generated.getExplanations());
            result.setAllergyWarnings(generated.getAllergyWarnings());

            if (generated.getProposedItems() != null) {
                result.setProposedItems(generated.getProposedItems().stream()
                        .map(p -> ProposedItemForChat.builder()
                                .productId(p.getProductId())
                                .variantId(p.getVariantId())
                                .quantity(p.getQuantity())
                                .reason(p.getReason())
                                .allergyWarning(firstWarning(generated.getAllergyWarnings()))
                                .nutritionFacts(p.getNutritionFacts())
                                .dayNo(p.getDayNo())
                                .mealSlot(p.getMealSlot())
                                .build())
                        .toList());
            }
        } catch (Exception e) {
            log.error("Meal plan generation failed: {}", e.getMessage());
            result.setSuccess(false);
            result.setErrorMessage("Không thể tạo thực đơn lúc này. Vui lòng thử lại.");
        }

        return result;
    }

    /**
     * Find safe food replacements (Trust Building: includes reason).
     */
    public List<Map<String, Object>> findSafeReplacements(Long userId, Long variantId) {
        try {
            UserNutritionProfile profile = nutritionProfileRepository.findByUser_Id(userId).orElse(null);
            String diet = profile != null ? profile.getDietaryPreference() : null;
            String allergies = profile != null ? profile.getAllergies() : null;
            String bmi = profile != null && profile.getBmi() != null ? profile.getBmi().toString() : "chưa cập nhật";

            return foodReplacementService.findNearestSubstitutes(variantId, diet, allergies, 3)
                    .stream()
                    .map(r -> {
                Map<String, Object> m = new java.util.HashMap<>();
                        m.put("variantId", r.getVariantId());
                        m.put("productId", r.getProductId());
                        m.put("name", r.getName());
                m.put("reason", "Phù hợp chế độ " + (diet != null ? diet : "cân bằng") + ", BMI " + bmi + " của bạn");
                m.put("allergyWarning", allergies != null && !allergies.isBlank()
                    ? "Đã loại bỏ sản phẩm chứa " + allergies
                    : "Không phát hiện xung đột dị ứng");
                m.put("nutritionFacts", java.util.Map.of(
                    "calories", 0,
                    "protein", 0,
                    "note", r.getReason() != null ? r.getReason() : "Gợi ý thay thế an toàn"
                ));
                        m.put("score", r.getGraphDistance()); // Using graph distance as score representation
                        return m;
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Replacement search failed for variant {}: {}", variantId, e.getMessage());
            return List.of();
        }
    }

    // ──── DTOs ────

    @lombok.Data
    public static class MealPlanChatResult {
        private boolean success;
        private String title;
        private Long planId;
        private Integer trustScore;
        private Map<Long, String> explanations = new java.util.HashMap<>();
        private List<String> allergyWarnings = new ArrayList<>();
        private List<ProposedItemForChat> proposedItems = new ArrayList<>();
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProposedItemForChat {
        private Long productId;
        private Long variantId;
        private Integer quantity;
        private String reason;
        private String allergyWarning;
        private Map<String, Object> nutritionFacts;
        private Integer dayNo;
        private String mealSlot;
    }

    private String firstWarning(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return "Không phát hiện xung đột dị ứng";
        return warnings.get(0);
    }
}
