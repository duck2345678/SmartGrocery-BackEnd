package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
    private GeminiAIService geminiAIService;

    @Autowired
    private ObjectMapper objectMapper;

    public Mono<MealPlan> generateAIPlan(Long userId, String goal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserNutritionProfile profile = nutritionProfileRepository.findByUser_Id(userId)
                .orElse(UserNutritionProfile.builder().dailyCalorieTarget(2000).build());

        List<ProductVariant> variants = productVariantRepository.findAll();
        String productContext = variants.stream()
                .limit(20)
                .map(v -> String.format("ID:%d, Name:%s, Price:%s", v.getId(), v.getVariantName(), v.getNetPrice()))
                .collect(Collectors.joining("; "));

        String prompt = String.format("""
                Hãy tạo một thực đơn 7 ngày cho người dùng có mục tiêu: %s.
                Chỉ số dinh dưỡng mục tiêu: %d calo/ngày.
                
                Sử dụng các sản phẩm thực tế từ kho hàng sau:
                %s
                
                Yêu cầu trả về kết quả định dạng JSON thuần túy (không dùng markdown ```json) như sau:
                {
                  "title": "Tên thực đơn",
                  "days": [
                    {
                       "dayNo": 1,
                       "meals": [
                         {"slot": "BREAKFAST", "variantId": 10, "quantity": 1, "estCalories": 300},
                         {"slot": "LUNCH", "variantId": 15, "quantity": 2, "estCalories": 600},
                         {"slot": "DINNER", "variantId": 12, "quantity": 1, "estCalories": 500}
                       ]
                    }
                  ]
                }
                """, goal, profile.getDailyCalorieTarget(), productContext);

        return geminiAIService.getGeminiResponse(prompt)
                .flatMap(jsonResponse -> {
                    try {
                        JsonNode root = objectMapper.readTree(jsonResponse);
                        
                        MealPlan plan = MealPlan.builder()
                                .user(user)
                                .title(root.get("title").asText())
                                .planDays(7)
                                .status("RECOMMENDED")
                                .build();
                        
                        MealPlan finalPlan = mealPlanRepository.save(plan);
                        
                        List<MealPlanItem> items = new ArrayList<>();
                        for (JsonNode dayNode : root.get("days")) {
                            int dayNo = dayNode.get("dayNo").asInt();
                            for (JsonNode mealNode : dayNode.get("meals")) {
                                Long variantId = mealNode.get("variantId").asLong();
                                ProductVariant variant = productVariantRepository.findById(variantId).orElse(null);
                                
                                if (variant != null) {
                                    items.add(MealPlanItem.builder()
                                            .mealPlan(finalPlan)
                                            .dayNo(dayNo)
                                            .mealSlot(mealNode.get("slot").asText())
                                            .variant(variant)
                                            .quantity(BigDecimal.valueOf(mealNode.get("quantity").asDouble()))
                                            .estCalories(BigDecimal.valueOf(mealNode.get("estCalories").asDouble()))
                                            .build());
                                }
                            }
                        }
                        mealPlanItemRepository.saveAll(items);
                        return Mono.just(finalPlan);
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Lỗi xử lý JSON thực đơn: " + e.getMessage()));
                    }
                });
    }

    public List<MealPlan> getByUserId(Long userId) {
        return mealPlanRepository.findByUser_IdOrderByCreatedAtDesc(userId);
    }
}
