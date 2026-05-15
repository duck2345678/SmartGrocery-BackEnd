package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import com.smartgrocery.backend.service.PromotionService;
import com.smartgrocery.backend.repository.jpa.UserNutritionProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.smartgrocery.backend.dto.PromotionCampaignDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentTools {

    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;
    private final ProductNodeRepository productNodeRepository;
    private final PromotionService promotionService;
    private final UserNutritionProfileRepository nutritionProfileRepository;
    private final NutritionChatIntegrator nutritionChatIntegrator;
    private final UserProfileConstraintService userProfileConstraintService;

    /**
     * Define the tools available for the AI Agent.
     */
    public List<ObjectNode> getAvailableTools() {
        ObjectNode searchParams = objectMapper.createObjectNode();
        searchParams.put("type", "object");
        ObjectNode searchProps = objectMapper.createObjectNode();
        ObjectNode queryProp = objectMapper.createObjectNode();
        queryProp.put("type", "string");
        queryProp.put("description", "Từ khóa hoặc câu mô tả cần tìm kiếm.");
        searchProps.set("query", queryProp);
        searchParams.set("properties", searchProps);
        ArrayNode requiredArray = objectMapper.createArrayNode();
        requiredArray.add("query");
        searchParams.set("required", requiredArray);

        ObjectNode emptyParams = objectMapper.createObjectNode();
        emptyParams.put("type", "object");
        emptyParams.set("properties", objectMapper.createObjectNode());

        ObjectNode selectMealParams = objectMapper.createObjectNode();
        selectMealParams.put("type", "object");
        ObjectNode selectMealProps = objectMapper.createObjectNode();
        ObjectNode optionNoProp = objectMapper.createObjectNode();
        optionNoProp.put("type", "integer");
        optionNoProp.put("description", "Số thứ tự của món ăn người dùng đã chọn (ví dụ: 1, 2, 3).");
        selectMealProps.set("optionNo", optionNoProp);
        selectMealParams.set("properties", selectMealProps);
        ArrayNode selectMealRequired = objectMapper.createArrayNode();
        selectMealRequired.add("optionNo");
        selectMealParams.set("required", selectMealRequired);

        ObjectNode suggestMealsParams = objectMapper.createObjectNode();
        suggestMealsParams.put("type", "object");
        ObjectNode suggestMealsProps = objectMapper.createObjectNode();
        ObjectNode optionsProp = objectMapper.createObjectNode();
        optionsProp.put("type", "array");
        optionsProp.put("description", "Danh sách 2-5 món ăn để hiển thị và lưu vào working memory.");
        ObjectNode optionItem = objectMapper.createObjectNode();
        optionItem.put("type", "object");
        ObjectNode optionProps = objectMapper.createObjectNode();
        ObjectNode suggestOptionNoProp = objectMapper.createObjectNode();
        suggestOptionNoProp.put("type", "integer");
        suggestOptionNoProp.put("description", "Số thứ tự món, bắt đầu từ 1.");
        ObjectNode titleProp = objectMapper.createObjectNode();
        titleProp.put("type", "string");
        titleProp.put("description", "Tên món ăn ngắn gọn.");
        ObjectNode ingredientsProp = objectMapper.createObjectNode();
        ingredientsProp.put("type", "array");
        ObjectNode ingredientItem = objectMapper.createObjectNode();
        ingredientItem.put("type", "string");
        ingredientsProp.set("items", ingredientItem);
        ingredientsProp.put("description", "Danh sách nguyên liệu chính đã đóng băng cho món này.");
        ObjectNode reasonProp = objectMapper.createObjectNode();
        reasonProp.put("type", "string");
        reasonProp.put("description", "Lý do món này phù hợp với ngữ cảnh người dùng.");
        optionProps.set("optionNo", suggestOptionNoProp);
        optionProps.set("title", titleProp);
        optionProps.set("ingredients", ingredientsProp);
        optionProps.set("reason", reasonProp);
        optionItem.set("properties", optionProps);
        ArrayNode optionRequired = objectMapper.createArrayNode();
        optionRequired.add("optionNo");
        optionRequired.add("title");
        optionRequired.add("ingredients");
        optionItem.set("required", optionRequired);
        optionsProp.set("items", optionItem);
        suggestMealsProps.set("options", optionsProp);
        suggestMealsParams.set("properties", suggestMealsProps);
        ArrayNode suggestMealsRequired = objectMapper.createArrayNode();
        suggestMealsRequired.add("options");
        suggestMealsParams.set("required", suggestMealsRequired);

        ObjectNode clearContextParams = objectMapper.createObjectNode();
        clearContextParams.put("type", "object");
        ObjectNode clearContextProps = objectMapper.createObjectNode();
        ObjectNode clearReasonProp = objectMapper.createObjectNode();
        clearReasonProp.put("type", "string");
        clearReasonProp.put("description", "Lý do đổi chủ đề hoặc xóa working memory.");
        clearContextProps.set("reason", clearReasonProp);
        clearContextParams.set("properties", clearContextProps);

        ObjectNode profileParams = objectMapper.createObjectNode();
        profileParams.put("type", "object");
        ObjectNode profileProps = objectMapper.createObjectNode();
        ObjectNode allergiesProp = objectMapper.createObjectNode();
        allergiesProp.put("type", "string");
        ObjectNode blockIngredientsProp = stringArrayProperty(
                "Nguyen lieu user khang dinh bi di ung, khong an duoc hoac can chan. Chi dien khi co phat ngon ro rang."
        );
        ObjectNode allowIngredientsProp = stringArrayProperty(
                "Nguyen lieu user khang dinh la khong di ung, an duoc, hoac can xoa khoi danh sach chan."
        );
        ObjectNode avoidMethodsProp = stringArrayProperty(
                "Phuong phap che bien can tranh, vi du: chien ran, luoc, cay."
        );
        ObjectNode preferencesProp = stringArrayProperty(
                "So thich an uong khong mang tinh chan tuyet doi, vi du: it muoi, it calo."
        );
        allergiesProp.put("description", "Danh sách dị ứng mới (ví dụ: 'tôm, cua').");
        ObjectNode goalsProp = objectMapper.createObjectNode();
        goalsProp.put("type", "string");
        goalsProp.put("description", "Mục tiêu sức khỏe mới (ví dụ: 'giảm cân').");
        profileProps.set("allergies", allergiesProp);
        profileProps.set("blockIngredients", blockIngredientsProp);
        profileProps.set("allowIngredients", allowIngredientsProp);
        profileProps.set("avoidMethods", avoidMethodsProp);
        profileProps.set("preferences", preferencesProp);
        profileProps.set("healthGoals", goalsProp);
        profileParams.set("properties", profileProps);

        return List.of(
                createToolDefinition("update_user_profile",
                        "Cập nhật thông tin hồ sơ người dùng (dị ứng, mục tiêu sức khỏe). Hãy gọi tool này NGAY KHI người dùng cung cấp thông tin mới về bản thân để hệ thống ghi nhớ vĩnh viễn.",
                        profileParams
                ),
                createToolDefinition("select_meal",
                        "Sử dụng khi người dùng ĐÃ CHỌN một món ăn cụ thể từ danh sách gợi ý. Tool này sẽ tự động tìm kiếm và chọn lọc chính xác các nguyên liệu cần thiết cho món ăn đó, ngăn chặn việc gợi ý sai nguyên liệu.",
                        selectMealParams
                ),
                createToolDefinition("suggest_meals",
                        "Sử dụng khi cần đề xuất hoặc thay thế danh sách món ăn. Mỗi option phải có nguyên liệu chính để server lưu vào working memory. BẮT BUỘC gọi inspect_user_context trước khi suggest_meals và không được đưa món/nguyên liệu vi phạm allergies, dietaryPreference, healthGoals của user.",
                        suggestMealsParams
                ),
                createToolDefinition("clear_context",
                        "Sử dụng khi user đổi chủ đề rõ ràng và danh sách món ăn hiện tại không còn liên quan.",
                        clearContextParams
                ),
                createToolDefinition("search_catalog",
                        "Tìm kiếm sản phẩm trong danh mục bằng Semantic Search và Full-text Search. Khi có allergies/dietaryPreference/healthGoals, query phải nêu rõ điều kiện tránh để AI tự loại sản phẩm không phù hợp trước khi đề xuất.",
                        searchParams
                ),
                createToolDefinition("get_promotions",
                        "Lấy danh sách các chương trình khuyến mãi, giảm giá hiện có. Sử dụng khi user hỏi về ưu đãi hoặc muốn tiết kiệm.",
                        emptyParams
                ),
                createToolDefinition("inspect_user_context",
                        "Kiểm tra thông tin cá nhân của người dùng như hồ sơ dinh dưỡng (BMI, mục tiêu), dị ứng, và giỏ hàng hiện tại. Gọi tool này TRƯỚC KHI đề xuất thực đơn hoặc món ăn để tránh dị ứng và phù hợp mục tiêu.",
                        emptyParams
                )
        );
    }

    private ObjectNode createToolDefinition(String name, String description, ObjectNode parameters) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", name);
        function.put("description", description);
        function.set("parameters", parameters);
        return tool;
    }

    private ObjectNode stringArrayProperty(String description) {
        ObjectNode prop = objectMapper.createObjectNode();
        prop.put("type", "array");
        prop.put("description", description);
        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "string");
        prop.set("items", item);
        return prop;
    }

    /**
     * Executes a tool by name and arguments.
     */
    public String executeTool(Long userId, String toolName, String argumentsJson) {
        log.info("Executing tool: {} with args: {}", toolName, argumentsJson);
        try {
            JsonNode args = argumentsJson != null && !argumentsJson.isBlank() 
                    ? objectMapper.readTree(argumentsJson) 
                    : objectMapper.createObjectNode();

            switch (toolName) {
                case "search_catalog":
                    String query = args.path("query").asText("");
                    return executeSearchCatalog(query);
                case "get_promotions":
                    return executeGetPromotions();
                case "inspect_user_context":
                    return executeInspectUserContext(userId);
                case "update_user_profile":
                    return executeUpdateUserProfile(userId, argumentsJson);
                case "select_meal":
                case "suggest_meals":
                case "clear_context":
                    return "{\"error\":\"Tool " + toolName + " requires chat session context and must be handled by ChatAssistantService.\"}";
                default:
                    return "{\"error\": \"Unknown tool: " + toolName + "\"}";
            }
        } catch (Exception e) {
            log.error("Tool execution failed: {}", e.getMessage());
            return "{\"error\": \"Execution failed: " + e.getMessage() + "\"}";
        }
    }

    private String executeUpdateUserProfile(Long userId, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            String allergies = args.path("allergies").asText(null);
            String healthGoals = args.path("healthGoals").asText(null);
            List<String> blockIngredients = readStringArray(args.path("blockIngredients"));
            List<String> allowIngredients = readStringArray(args.path("allowIngredients"));
            List<String> avoidMethods = readStringArray(args.path("avoidMethods"));
            List<String> preferences = readStringArray(args.path("preferences"));

            nutritionProfileRepository.findByUser_Id(userId).ifPresent(profile -> {
                if (allergies != null && !allergies.isBlank()) {
                    Set<String> clearedTerms = userProfileConstraintService.extractClearedAllergyTerms(allergies);
                    if (clearedTerms.isEmpty()) {
                        profile.setAllergies(allergies);
                    } else {
                        profile.setAllergies(userProfileConstraintService.removeClearedAllergyTerms(
                                profile.getAllergies(),
                                allergies
                        ));
                    }
                }
                if (!allowIngredients.isEmpty()) {
                    profile.setAllergies(removeTermsFromLegacyAllergies(profile.getAllergies(), allowIngredients));
                }
                if (!blockIngredients.isEmpty() || !allowIngredients.isEmpty()
                        || !avoidMethods.isEmpty() || !preferences.isEmpty()) {
                    profile.setFoodConstraints(userProfileConstraintService.mergeFoodConstraints(
                            profile.getFoodConstraints(),
                            blockIngredients,
                            allowIngredients,
                            avoidMethods,
                            preferences
                    ));
                }
                if (healthGoals != null && !healthGoals.isBlank()) {
                    profile.setHealthGoals(healthGoals);
                }
                nutritionProfileRepository.save(profile);
            });

            return "Hồ sơ người dùng đã được cập nhật thành công. AI sẽ ghi nhớ thông tin này cho các câu hỏi sau.";
        } catch (Exception e) {
            return "Lỗi khi cập nhật hồ sơ: " + e.getMessage();
        }
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        });
        return values;
    }

    private String removeTermsFromLegacyAllergies(String currentAllergies, List<String> allowIngredients) {
        String updated = currentAllergies;
        for (String term : allowIngredients) {
            updated = userProfileConstraintService.removeClearedAllergyTerms(updated, "khong di ung " + term);
        }
        return updated;
    }

    private String executeSearchCatalog(String query) {
        if (query.isBlank()) return "[]";

        // 1. Semantic Search
        List<Double> vector = embeddingService.getEmbeddingSync(query);
        List<ProductNode> semanticResults = List.of();
        if (vector != null && !vector.isEmpty()) {
            semanticResults = productNodeRepository.searchByVector(vector, 0.7, 10);
        }

        // 2. Full-text Search as fallback/complement
        List<ProductNode> fulltextResults = productNodeRepository.searchFullText(query);

        // Merge results (simple distinct by ID)
        java.util.LinkedHashMap<Long, ProductNode> merged = new java.util.LinkedHashMap<>();
        semanticResults.forEach(p -> merged.putIfAbsent(p.getProductId(), p));
        fulltextResults.forEach(p -> merged.putIfAbsent(p.getProductId(), p));

        // Format to JSON string, limiting to top 5 to prevent context window overflow
        ArrayNode results = objectMapper.createArrayNode();
        int count = 0;
        for (ProductNode p : merged.values()) {
            if (count >= 5) break;
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", p.getProductId());
            node.put("name", p.getName());
            node.put("category", p.getCategoryName());
            node.put("description", p.getDescription());
            node.put("isStaple", Boolean.TRUE.equals(p.getIsStaple()));
            results.add(node);
            count++;
        }
        return results.toString();
    }

    private String executeGetPromotions() {
        try {
            List<PromotionCampaignDto> campaigns = promotionService.getAllCampaigns();
            if (campaigns.isEmpty()) {
                return "Không có chương trình khuyến mãi nào đang diễn ra.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Danh sách khuyến mãi:\n");
            for (PromotionCampaignDto c : campaigns) {
                sb.append("- Mã: ").append(c.getCampaignCode() != null ? c.getCampaignCode() : "N/A")
                  .append(" | Tên: ").append(c.getCampaignName() != null ? c.getCampaignName() : "N/A")
                  .append(" | Loại: ").append(c.getCampaignType() != null ? c.getCampaignType() : "N/A")
                  .append(" | HSD: ").append(c.getEndsAt() != null ? c.getEndsAt().toString() : "N/A")
                  .append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private String executeInspectUserContext(Long userId) {
        ObjectNode ctx = objectMapper.createObjectNode();
        
        nutritionProfileRepository.findByUser_Id(userId).ifPresent(profile -> {
            ctx.put("healthGoals", profile.getHealthGoals());
            ctx.put("allergies", profile.getAllergies());
            ctx.put("dietaryPreference", profile.getDietaryPreference());
            ctx.put("foodConstraints", profile.getFoodConstraints());
            ctx.put("bmi", profile.getBmi() != null ? profile.getBmi().floatValue() : null);
        });

        try {
            String cartContext = nutritionChatIntegrator.analyzeCartForChat(userId);
            ctx.put("cartSummary", cartContext);
        } catch (Exception e) {
            ctx.put("cartSummary", "Không lấy được giỏ hàng");
        }
        
        return ctx.toString();
    }
}
