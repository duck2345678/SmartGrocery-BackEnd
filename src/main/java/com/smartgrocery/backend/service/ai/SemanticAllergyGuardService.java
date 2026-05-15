package com.smartgrocery.backend.service.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.UserNutritionProfileRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartgrocery.backend.dto.ChatResponsePayload;
import com.smartgrocery.backend.dto.ProposedItemDto;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticAllergyGuardService {

    private final OpenRouterClient openRouterClient;
    private final ProductRepository productRepository;
    private final UserNutritionProfileRepository nutritionProfileRepository;
    private final ObjectMapper objectMapper;
    private final com.smartgrocery.backend.config.OpenRouterConfig config;


    // Cache key: productId + ":" + normalizedAllergies
    private final Cache<String, SafetyResult> safetyCache = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(10000)
            .build();

    public enum DangerLevel {
        SAFE,
        MAY_CONTAIN,
        DIRECT_ALLERGEN
    }

    @Data
    @Builder
    public static class SafetyResult {
        private DangerLevel dangerLevel;
        private String reason;
    }

    /**
     * Checks a list of proposed items against user allergies using an LLM.
     * Only hard-blocks DIRECT_ALLERGEN.
     */
    @Transactional
    public void enforceSemanticGuard(ChatResponsePayload payload, Long userId) {
        if (payload.getProposedItems() == null || payload.getProposedItems().isEmpty()) {
            return;
        }

        String allergies = nutritionProfileRepository.findByUser_Id(userId)
                .map(p -> p.getAllergies())
                .orElse(null);

        if (allergies == null || allergies.isBlank()) {
            return;
        }

        List<ProposedItemDto> items = payload.getProposedItems();
        List<Long> productIds = items.stream()
                .map(ProposedItemDto::getProductId)
                .collect(Collectors.toList());

        // FETCH JOIN to avoid LazyInitializationException outside transaction
        List<Product> products = productRepository.findAllByIdWithCategory(productIds);
        List<Long> itemsToRemove = new ArrayList<>();
        Map<Long, SafetyResult> results = new HashMap<>();

        // 1. Check cache first
        List<Product> productsToProcess = new ArrayList<>();
        for (Product product : products) {
            String cacheKey = product.getId() + ":" + allergies.toLowerCase().trim();
            SafetyResult cached = safetyCache.getIfPresent(cacheKey);
            if (cached != null) {
                results.put(product.getId(), cached);
            } else {
                productsToProcess.add(product);
            }
        }

        // 2. Call LLM for remaining products
        if (!productsToProcess.isEmpty()) {
            try {
                Map<Long, SafetyResult> llmResults = callLlmGuard(productsToProcess, allergies);
                results.putAll(llmResults);
                
                // Cache results
                llmResults.forEach((id, res) -> {
                    safetyCache.put(id + ":" + allergies.toLowerCase().trim(), res);
                });
            } catch (Exception e) {
                log.warn("Semantic Allergy Guard LLM call failed: {}. Falling back to keyword safety.", e.getMessage());
                // Fail-safe: don't remove anything, but add a warning to reply
                String warning = "\n[Lưu ý] Hệ thống kiểm tra an toàn chuyên sâu đang bận. Bạn vui lòng kiểm tra kỹ thành phần sản phẩm nếu có tiền sử dị ứng nghiêm trọng.";
                if (payload.getReply() != null && !payload.getReply().contains("kiểm tra kỹ thành phần")) {
                    payload.setReply(payload.getReply() + warning);
                }
                return;
            }
        }

        // 3. Process results
        for (Iterator<ProposedItemDto> it = payload.getProposedItems().iterator(); it.hasNext(); ) {
            ProposedItemDto item = it.next();
            SafetyResult res = results.get(item.getProductId());
            
            if (res != null && res.getDangerLevel() == DangerLevel.DIRECT_ALLERGEN) {
                log.info("Semantic Guard: Removing product {} due to direct allergen: {}", item.getProductId(), res.getReason());
                itemsToRemove.add(item.getProductId());
                it.remove();
                
                // Add to remove reasons if applicable
                if (payload.getRemoveReasons() == null) {
                    payload.setRemoveReasons(new HashMap<>());
                }
                payload.getRemoveReasons().put(item.getProductId(), 
                    "Vi phạm dị ứng: " + res.getReason());
            } else if (res != null && res.getDangerLevel() == DangerLevel.MAY_CONTAIN) {
                // Just add a warning explanation, don't remove
                if (payload.getExplanations() == null) {
                    payload.setExplanations(new HashMap<>());
                }
                String current = payload.getExplanations().getOrDefault(item.getProductId(), "");
                String warning = "Lưu ý: Có thể chứa " + res.getReason();
                payload.getExplanations().put(item.getProductId(), 
                    current.isEmpty() ? warning : current + ". " + warning);
            }
        }

        if (!itemsToRemove.isEmpty()) {
            if (payload.getRecommendedProductIds() != null) {
                // Ensure list is mutable to avoid UnsupportedOperationException
                List<Long> mutableRecs = new ArrayList<>(payload.getRecommendedProductIds());
                mutableRecs.removeAll(itemsToRemove);
                payload.setRecommendedProductIds(mutableRecs);
            }
            String removalNote = "\n(Đã loại bỏ một số sản phẩm không an toàn theo hồ sơ dị ứng của bạn)";
            if (payload.getReply() != null && !payload.getReply().contains(removalNote)) {
                payload.setReply(payload.getReply() + removalNote);
            }
        }
    }

    private Map<Long, SafetyResult> callLlmGuard(List<Product> products, String allergies) throws Exception {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là chuyên gia an toàn thực phẩm. Hãy kiểm tra các sản phẩm sau đây đối với tiền sử dị ứng của người dùng.\n\n");
        prompt.append("Dị ứng của người dùng: ").append(allergies).append("\n\n");
        prompt.append("Danh sách sản phẩm:\n");
        for (Product p : products) {
            prompt.append("- [ID: ").append(p.getId()).append("] ").append(p.getName());
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                prompt.append(" (Mô tả: ").append(p.getDescription()).append(")");
            }
            prompt.append("\n");
        }
        prompt.append("\nPhân loại từng sản phẩm thành một trong 3 mức:\n");
        prompt.append("1. DIRECT_ALLERGEN: Chắc chắn chứa thành phần gây dị ứng.\n");
        prompt.append("2. MAY_CONTAIN: Có khả năng chứa hoặc thường đi kèm thành phần gây dị ứng (ví dụ: nước mắm trong món nộm khi dị ứng hải sản).\n");
        prompt.append("3. SAFE: An toàn.\n\n");
        prompt.append("Trả về kết quả dưới định dạng JSON array:\n");
        prompt.append("[{\"id\": 123, \"dangerLevel\": \"DIRECT_ALLERGEN\", \"reason\": \"Chứa cà chua trong thành phần sốt\"}, ...]");

        // Use Pass 1 model for speed
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", prompt.toString());
        messages.add(systemMsg);

        OpenRouterClient.AiCompletionResult result = openRouterClient.chatCompletion(
                prompt.toString(),
                List.of(),
                List.of(),
                config.getPass1Model(),
                Duration.ofSeconds(10)
        ).block();



        if (result == null || !result.isSuccess() || result.getReply() == null) {
            throw new RuntimeException("LLM failed to return safety analysis");
        }

        String jsonContent = extractJson(result.getReply());
        JsonNode root = objectMapper.readTree(jsonContent);
        Map<Long, SafetyResult> resultMap = new HashMap<>();

        if (root.isArray()) {
            for (JsonNode node : root) {
                long id = node.path("id").asLong();
                String rawLevel = node.path("dangerLevel").asText("SAFE").toUpperCase().trim();
                DangerLevel level;
                try {
                    level = DangerLevel.valueOf(rawLevel);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid dangerLevel from AI: {}. Defaulting to SAFE.", rawLevel);
                    level = DangerLevel.SAFE;
                }
                String reason = node.path("reason").asText("");
                resultMap.put(id, SafetyResult.builder().dangerLevel(level).reason(reason).build());
            }
        }

        return resultMap;
    }

    private String extractJson(String text) {
        int start = text.indexOf("[");
        int end = text.lastIndexOf("]");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
