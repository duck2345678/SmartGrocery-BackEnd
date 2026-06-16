package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.ChatSession;
import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.ShoppingScenarioAlias;
import com.smartgrocery.backend.repository.jpa.ChatSessionRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentResolverService {

    private final CatalogCacheService catalogCacheService;
    private final ProductVariantRepository productVariantRepository;
    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;
    private final ConversationStateManager stateManager;
    private final ChatSessionRepository chatSessionRepository;

    private static final Pattern SELECTION_PATTERN = Pattern.compile("^(món\\s+)?(\\d+)(\\s+và\\s+(\\d+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISCOUNT_KEYWORD_PATTERN = Pattern.compile("(?iu).*(giam\\s*gia|sale|khuyen\\s*mai|uu\\s*dai).*");

    public record IntentResult(IntentType intent, String entity, Double confidence) {
        public enum IntentType {
            BUY_PRODUCT,
            MEAL_RECIPE,
            CHECK_DISCOUNT,
            SHOPPING_SCENARIO,
            MEAL_SELECTION,
            CONTEXT_CONTINUATION,
            GENERAL_CHAT
        }
    }

    public IntentResult resolveIntent(String userMessage, String sessionId, Long userId) {
        if (userMessage == null || userMessage.isBlank()) {
            return new IntentResult(IntentResult.IntentType.GENERAL_CHAT, null, 1.0);
        }

        String trimmed = userMessage.trim();
        String normalized = normalizeVietnamese(trimmed);

        // 1. Selection Rules (Fast-Path)
        if (SELECTION_PATTERN.matcher(trimmed).matches() || trimmed.matches("^\\d+(\\s*[,/\\s+]\\s*\\d+)*$")) {
            log.info("[IntentResolver] Match: SELECTION_RULES -> MEAL_SELECTION");
            return new IntentResult(IntentResult.IntentType.MEAL_SELECTION, trimmed, 1.0);
        }

        // 2. Context Continuation (High Priority to prevent other matches from stealing the context)
        boolean isContinuationPattern = trimmed.matches("(?iu).*\\b(thêm|them|bớt|bot|nữa|nua|người|nguoi|cái|cai|chai|vỉ|hộp|loại|phần|suất|chọn lại|huỷ|huy)\\b.*")
                || trimmed.matches("^\\d+(\\s+(người|nguoi|cái|cai|chai|phần|suất|món|mon))?$");
        if (isContinuationPattern) {
            ConversationStateManager.ConversationState state = getConversationState(sessionId);
            if (state != null) {
                log.info("[IntentResolver] Match: CONTEXT_CONTINUATION -> Last Intent: {} for entity: {}", state.getLastIntent(), state.getScenarioCode());
                try {
                    IntentResult.IntentType type = IntentResult.IntentType.valueOf(state.getLastIntent().toUpperCase());
                    return new IntentResult(type, state.getScenarioCode(), 1.0);
                } catch (IllegalArgumentException e) {
                    log.warn("[IntentResolver] Invalid stored last intent: {}", state.getLastIntent());
                }
            }
        }

        // 3. Discount Rules
        if (DISCOUNT_KEYWORD_PATTERN.matcher(normalized).matches() || normalized.contains("wishlist") || normalized.contains("yeu thich")) {
            String keyword = extractSearchKeyword(trimmed);
            log.info("[IntentResolver] Match: DISCOUNT_RULES -> CHECK_DISCOUNT for '{}'", keyword);
            return new IntentResult(IntentResult.IntentType.CHECK_DISCOUNT, keyword, 1.0);
        }

        // Extract potential search keyword
        String searchKeyword = extractSearchKeyword(trimmed);

        // 4. Exact Product Match (Token containment overlap >= 1.0)
        if (searchKeyword.length() >= 2) {
            String normalizedSearch = normalizeVietnamese(searchKeyword);
            List<ProductVariant> exactMatches = productVariantRepository.findActiveVariantsByKeywordNameOnly(normalizedSearch);
            if (exactMatches.isEmpty()) {
                exactMatches = productVariantRepository.findTop10ActiveByKeyword(normalizedSearch);
            }
            for (ProductVariant v : exactMatches) {
                if (v.getProduct() != null && v.getProduct().getName() != null) {
                    String prodName = v.getProduct().getName();
                    double overlap = calculateTokenOverlap(searchKeyword, prodName);
                    if (overlap >= 1.0) {
                        log.info("[IntentResolver] Match: EXACT_PRODUCT_MATCH -> BUY_PRODUCT for '{}' (overlap=1.0)", searchKeyword);
                        return new IntentResult(IntentResult.IntentType.BUY_PRODUCT, searchKeyword, 1.0);
                    }
                }
            }
        }

        // 5. Scenario Alias Match (In-Memory Cache lookup, using padded boundary checks)
        List<ShoppingScenarioAlias> aliases = catalogCacheService.getCachedScenarioAliases();
        String normalizedTrimmed = normalizeVietnamese(trimmed);
        for (ShoppingScenarioAlias alias : aliases) {
            if (alias.getNormalizedAlias() != null && alias.getScenario() != null) {
                String normalizedAlias = " " + alias.getNormalizedAlias() + " ";
                String normalizedTrimmedWithSpaces = " " + normalizedTrimmed + " ";
                if (normalizedTrimmedWithSpaces.contains(normalizedAlias)) {
                    log.info("[IntentResolver] Match: SCENARIO_ALIAS_MATCH -> SHOPPING_SCENARIO for '{}'", alias.getScenario().getCode());
                    return new IntentResult(IntentResult.IntentType.SHOPPING_SCENARIO, alias.getScenario().getCode(), 1.0);
                }
            }
        }

        // 6. Meal Match
        List<Meal> meals = catalogCacheService.getCachedMeals();
        for (Meal m : meals) {
            if (m.getName() != null) {
                String mNameNorm = normalizeVietnamese(m.getName());
                if (mNameNorm.equals(normalizedTrimmed) 
                        || normalizedTrimmed.contains("nấu " + mNameNorm) 
                        || normalizedTrimmed.contains("nau " + mNameNorm)
                        || normalizedTrimmed.contains("cách làm " + mNameNorm)
                        || normalizedTrimmed.contains("cach lam " + mNameNorm)) {
                    log.info("[IntentResolver] Match: MEAL_MATCH -> MEAL_RECIPE for '{}'", m.getName());
                    return new IntentResult(IntentResult.IntentType.MEAL_RECIPE, m.getName(), 1.0);
                }
            }
        }

        // 7. Fuzzy Product Match (Token overlap >= 0.70)
        if (searchKeyword.length() >= 2) {
            String normalizedSearch = normalizeVietnamese(searchKeyword);
            List<ProductVariant> fuzzyMatches = productVariantRepository.searchActiveForSubstitution(normalizedSearch);
            for (ProductVariant v : fuzzyMatches) {
                if (v.getProduct() != null && v.getProduct().getName() != null) {
                    String prodName = v.getProduct().getName();
                    double overlap = calculateTokenOverlap(searchKeyword, prodName);
                    if (overlap >= 0.70) {
                        log.info("[IntentResolver] Match: FUZZY_PRODUCT_MATCH -> BUY_PRODUCT for '{}' (overlap={})", searchKeyword, overlap);
                        return new IntentResult(IntentResult.IntentType.BUY_PRODUCT, searchKeyword, 0.9);
                    }
                }
            }
        }

        // 8. OpenRouter Fallback Classifier
        log.info("[IntentResolver] Fallback to OpenRouter classification for: '{}'", trimmed);
        return classifyWithAi(trimmed);
    }

    private ConversationStateManager.ConversationState getConversationState(String sessionId) {
        if (sessionId == null) return null;
        try {
            Long sessId = Long.parseLong(sessionId);
            Optional<ChatSession> sessionOpt = chatSessionRepository.findById(sessId);
            if (sessionOpt.isPresent()) {
                String ctxType = sessionOpt.get().getContextType();
                if (ctxType != null) {
                    if (ctxType.startsWith("SCENARIO:")) {
                        return ConversationStateManager.ConversationState.builder()
                                .lastIntent("SHOPPING_SCENARIO")
                                .scenarioCode(ctxType.substring(9))
                                .build();
                    } else if (ctxType.startsWith("MEAL:")) {
                        return ConversationStateManager.ConversationState.builder()
                                .lastIntent("MEAL_RECIPE")
                                .scenarioCode(ctxType.substring(5))
                                .build();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return stateManager.getState(sessionId);
    }

    private double calculateTokenOverlap(String query, String target) {
        if (query == null || target == null || query.isBlank() || target.isBlank()) {
            return 0.0;
        }
        String qNorm = normalizeVietnamese(query);
        String tNorm = normalizeVietnamese(target);
        
        Set<String> qTokens = Arrays.stream(qNorm.split("\\s+"))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());
        Set<String> tTokens = Arrays.stream(tNorm.split("\\s+"))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());
        
        if (qTokens.isEmpty() || tTokens.isEmpty()) {
            return 0.0;
        }
        
        long intersection = qTokens.stream().filter(tTokens::contains).count();
        return (double) intersection / Math.min(qTokens.size(), tTokens.size());
    }

    private IntentResult classifyWithAi(String userMessage) {
        String systemPrompt = """
                Bạn là bộ phận phân loại ý định (Intent Classifier) cho ứng dụng SmartGrocery.
                Phân loại tin nhắn của người dùng thành một trong các ý định sau:
                - BUY_PRODUCT: Mua sản phẩm cụ thể (ví dụ: "lấy cho tôi chai tương ớt", "mua trứng gà").
                - MEAL_RECIPE: Hỏi công thức nấu ăn, gợi ý món ăn (ví dụ: "ăn gì hôm nay", "nấu món gì ngon", "tối nay nấu gì").
                - CHECK_DISCOUNT: Hỏi về giảm giá, khuyến mãi (ví dụ: "hôm nay có sale gì không").
                - SHOPPING_SCENARIO: Gợi ý mua sắm theo tình huống (ví dụ: "tối nay có khách", "nhà bẩn quá", "chuẩn bị đi picnic").
                - GENERAL_CHAT: Chào hỏi, tán gẫu thông thường (ví dụ: "xin chào", "bạn là ai").
                
                Hãy trả về một đối tượng JSON duy nhất có dạng:
                {
                  "intent": "BUY_PRODUCT" | "MEAL_RECIPE" | "CHECK_DISCOUNT" | "SHOPPING_SCENARIO" | "GENERAL_CHAT",
                  "confidence": 0.0 đến 1.0,
                  "entity": "Tên sản phẩm/món ăn/mã tình huống (như PICNIC, CLEANING, REFILL_FRIDGE) hoặc null",
                  "reason": "Giải thích ngắn gọn"
                }
                """;

        try {
            OpenRouterClient.AiCompletionResult result = openRouterClient
                    .chatCompletion(systemPrompt, List.of(Map.of("role", "user", "content", userMessage)), null, Duration.ofSeconds(5))
                    .block();

            if (result != null && result.isSuccess() && result.getReply() != null) {
                JsonNode root = objectMapper.readTree(result.getReply());
                String intentStr = root.path("intent").asText("GENERAL_CHAT").trim();
                // Safe parsing and mapping to prevent IllegalArgumentException
                intentStr = intentStr.replace(" ", "_").toUpperCase();
                
                double confidence = root.path("confidence").asDouble(1.0);
                JsonNode entityNode = root.get("entity");
                String entity = entityNode == null || entityNode.isNull() ? null : entityNode.asText(null);

                IntentResult.IntentType type = IntentResult.IntentType.GENERAL_CHAT;
                try {
                    type = IntentResult.IntentType.valueOf(intentStr);
                } catch (IllegalArgumentException e) {
                    log.warn("[IntentResolver] Unknown fallback intent: {}", intentStr);
                }

                if (confidence >= 0.7) {
                    return new IntentResult(type, entity, confidence);
                }
            }
        } catch (Exception e) {
            log.error("[IntentResolver] AI fallback classification error: {}", e.getMessage());
        }

        return new IntentResult(IntentResult.IntentType.GENERAL_CHAT, null, 1.0);
    }

    private String extractSearchKeyword(String message) {
        if (message == null) return "";
        String clean = message
                .replaceAll("(?iuU)\\b(có|co|không|khong|ko|k|hok|đang|dang|được|duoc|hôm nay|hom nay|nào|nao|gì|gi|nhỉ|nhi|hả|ha|à|a|vậy|vay|khuyến mại|khuyen mai|khuyến mãi|khuyen mai|giảm giá|giam gia|sale|sản phẩm|san pham|mặt hàng|mat hang|mua|tìm|tim|cho|thêm|them|cho tôi|cho toi|giúp|giup|cần|can|muốn|muon|wishlist|yêu thích|yeu thich|đã lưu|da luu|danh sách|danh sach)\\b", " ")
                .replaceAll("[?!.,/]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return clean;
    }

    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd').replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
