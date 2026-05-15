package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.jpa.*;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.ai.OpenRouterClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class MealPlanService {

    @Autowired
    private MealPlanRepository mealPlanRepository;

    @Autowired
    private MealPlanItemRepository mealPlanItemRepository;

    @Autowired
    private UserNutritionProfileRepository nutritionProfileRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OpenRouterClient openRouterClient;

    @Autowired
    private ProductNodeRepository productNodeRepository;

    public List<MealPlan> getByUserId(Long userId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        return mealPlanRepository.findByUser_IdOrderByCreatedAtDesc(userId);
    }

    /**
     * Tạo kế hoạch ăn AI sử dụng OpenRouter (Gemini 2.0 Flash).
     * Tích hợp UserNutritionProfile để lọc dị ứng + BMI-aware.
     */
    public MealPlan generateAIPlan(Long userId, String goal) {
        return generateAIPlanStructured(userId, goal).getMealPlan();
    }

    /**
     * Trả về cả MealPlan đã lưu DB + structured payload cho frontend/chat.
     */
    public MealPlanGenerationResult generateAIPlanStructured(Long userId, String goal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserNutritionProfile profile = nutritionProfileRepository.findByUser_Id(userId).orElse(null);
        Set<String> allergyTokens = extractAllergyTokens(profile != null ? profile.getAllergies() : null);

        // Build context from nutrition profile
        StringBuilder context = new StringBuilder();
        context.append("Mục tiêu: ").append(goal != null ? goal : "Bữa ăn cân bằng 7 ngày").append("\n");

        if (profile != null) {
            if (profile.getAllergies() != null) {
                context.append("DỊ ỨNG (TUYỆT ĐỐI KHÔNG DÙNG): ").append(profile.getAllergies()).append("\n");
            }
            if (profile.getDietaryPreference() != null) {
                context.append("Chế độ ăn: ").append(profile.getDietaryPreference()).append("\n");
            }
            if (profile.getBmi() != null) {
                context.append("BMI: ").append(profile.getBmi()).append("\n");
            }
            if (profile.getDailyCalorieTarget() != null) {
                context.append("Mục tiêu calo/ngày: ").append(profile.getDailyCalorieTarget()).append("\n");
            }
        }

        // Add available products
        try {
            List<ProductNode> products = productNodeRepository.findAll()
                    .stream().limit(40).collect(Collectors.toList());
            if (!products.isEmpty()) {
                context.append("\nSẢN PHẨM CÓ SẴN:\n");
                for (ProductNode p : products) {
                    context.append(String.format("- ID:%d | %s\n", p.getProductId(), p.getName()));
                }
            }
        } catch (Exception e) {
            // Neo4j not available, continue without product list
        }

        String systemPrompt = """
            Bạn là AI dinh dưỡng chuyên tạo thực đơn 7 ngày. Trả về JSON (KHÔNG markdown):
            {
              "title": "Thực đơn ...",
              "days": [
                {
                  "dayNo": 1,
                  "meals": [
                    {"slot": "BREAKFAST", "productId": 5, "quantity": 1, "estCalories": 350},
                    {"slot": "LUNCH", "productId": 12, "quantity": 2, "estCalories": 500}
                  ]
                }
              ]
            }
            Quy tắc: dùng productId từ danh sách sản phẩm, tránh dị ứng, phù hợp BMI.
            """;

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", context.toString())
        );

        OpenRouterClient.AiCompletionResult result = openRouterClient
                .chatCompletion(systemPrompt, messages)
                .block();

        if (result == null || !result.isSuccess()) {
            throw new RuntimeException("AI meal plan generation failed");
        }

        return parseMealPlanResponse(user, result.getReply(), goal, profile, allergyTokens);
    }

    private MealPlanGenerationResult parseMealPlanResponse(User user,
                                                           String aiReply,
                                                           String goal,
                                                           UserNutritionProfile profile,
                                                           Set<String> allergyTokens) {
        MealPlan plan = MealPlan.builder()
                .user(user)
                .title(goal != null ? goal : "Thực đơn AI")
                .planDays(7)
                .status("RECOMMENDED")
                .build();

        MealPlanGenerationResult structured = MealPlanGenerationResult.builder()
                .trustScore(80)
                .explanations(new HashMap<>())
                .proposedItems(new ArrayList<>())
                .allergyWarnings(new ArrayList<>())
                .build();

        int acceptedItems = 0;
        int skippedItems = 0;

        try {
            String json = extractJson(aiReply);
            if (json != null) {
                JsonNode root = objectMapper.readTree(json);

                if (root.has("title")) {
                    plan.setTitle(root.get("title").asText());
                }

                plan = mealPlanRepository.save(plan);

                List<MealPlanItem> items = new ArrayList<>();
                if (root.has("days") && root.get("days").isArray()) {
                    for (JsonNode day : root.get("days")) {
                        int dayNo = day.path("dayNo").asInt(1);
                        if (day.has("meals") && day.get("meals").isArray()) {
                            for (JsonNode meal : day.get("meals")) {
                                long productId = meal.path("productId").asLong();
                                ProductVariant variant = productVariantRepository.findByProduct_Id(productId)
                                        .stream().findFirst().orElse(null);
                                if (variant == null) {
                                    skippedItems++;
                                    continue;
                                }

                                String productName = variant.getProduct() != null ? variant.getProduct().getName() : "";
                                if (containsAllergy(productName, allergyTokens)) {
                                    skippedItems++;
                                    structured.getAllergyWarnings().add("Đã loại bỏ sản phẩm chứa thành phần dị ứng: " + productName);
                                    continue;
                                }

                                MealPlanItem item = MealPlanItem.builder()
                                        .mealPlan(plan)
                                        .dayNo(dayNo)
                                        .mealSlot(meal.path("slot").asText("LUNCH"))
                                        .variant(variant)
                                        .quantity(BigDecimal.valueOf(meal.path("quantity").asDouble(1)))
                                        .estCalories(BigDecimal.valueOf(meal.path("estCalories").asDouble(0)))
                                        .build();
                                items.add(item);
                                acceptedItems++;

                                Long explainKey = variant.getProduct() != null ? variant.getProduct().getId() : variant.getId();
                                structured.getExplanations().put(
                                        explainKey,
                                        buildReasonText(profile, item, variant)
                                );
                                structured.getProposedItems().add(
                                        MealPlanProposedItem.builder()
                                                .productId(explainKey)
                                                .variantId(variant.getId())
                                                .quantity(item.getQuantity() != null ? item.getQuantity().intValue() : 1)
                                                .dayNo(dayNo)
                                                .mealSlot(item.getMealSlot())
                                                .reason(buildReasonText(profile, item, variant))
                                                .nutritionFacts(buildNutritionFacts(item))
                                                .build()
                                );
                            }
                        }
                    }
                }

                if (!items.isEmpty()) {
                    mealPlanItemRepository.saveAll(items);
                    plan.setItems(items);
                }
            } else {
                plan = mealPlanRepository.save(plan);
            }
        } catch (Exception e) {
            plan = mealPlanRepository.save(plan);
        }

        int trust = 75 + Math.min(20, acceptedItems * 2) - Math.min(15, skippedItems * 3);
        structured.setTrustScore(Math.max(40, Math.min(95, trust)));
        structured.setMealPlan(plan);
        return structured;
    }

    private Set<String> extractAllergyTokens(String allergies) {
        if (allergies == null || allergies.isBlank()) return Set.of();
        Set<String> tokens = new HashSet<>();
        for (String token : allergies.split("[,;/]")) {
            String t = token == null ? "" : token.trim().toLowerCase();
            if (!t.isBlank()) tokens.add(t);
        }
        return tokens;
    }

    private boolean containsAllergy(String productName, Set<String> allergyTokens) {
        if (productName == null || productName.isBlank() || allergyTokens == null || allergyTokens.isEmpty()) {
            return false;
        }
        String name = productName.toLowerCase();
        return allergyTokens.stream().anyMatch(name::contains);
    }

    private String buildReasonText(UserNutritionProfile profile, MealPlanItem item, ProductVariant variant) {
        String bmiText = profile != null && profile.getBmi() != null ? "BMI " + profile.getBmi() : "BMI chưa cập nhật";
        String goal = profile != null && profile.getHealthGoals() != null ? profile.getHealthGoals() : "chế độ cân bằng";
        return "Phù hợp chế độ " + goal + ", " + bmiText + " của bạn";
    }

    private Map<String, Object> buildNutritionFacts(MealPlanItem item) {
        Map<String, Object> facts = new HashMap<>();
        facts.put("calories", item.getEstCalories() != null ? item.getEstCalories() : BigDecimal.ZERO);
        BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
        facts.put("protein", qty.multiply(BigDecimal.valueOf(10))); // heuristic estimate
        return facts;
    }

    private String extractJson(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MealPlanGenerationResult {
        private MealPlan mealPlan;
        private Integer trustScore;
        private Map<Long, String> explanations;
        private List<String> allergyWarnings;
        private List<MealPlanProposedItem> proposedItems;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MealPlanProposedItem {
        private Long productId;
        private Long variantId;
        private Integer quantity;
        private Integer dayNo;
        private String mealSlot;
        private String reason;
        private Map<String, Object> nutritionFacts;
    }
}

