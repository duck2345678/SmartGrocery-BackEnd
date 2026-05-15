package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.jpa.*;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import com.smartgrocery.backend.service.CartInspectionService;
import com.smartgrocery.backend.service.PromotionService;
import com.smartgrocery.backend.service.CartInspectionService.CartInspectionReport;
import com.smartgrocery.backend.config.OpenRouterConfig;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MEMM (Motivation-Expectation Management Model) Chat Engine.
 * 4-stage pipeline: Motivation → Personalized HCI → Trust Response → Expectation Management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAssistantService {

    private final OpenRouterClient openRouterClient;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserNutritionProfileRepository nutritionProfileRepository;
    private final ProductNodeRepository productNodeRepository;
    private final CartInspectionService cartInspectionService;
    private final NutritionChatIntegrator nutritionChatIntegrator;
    private final MemmFeedbackService memmFeedbackService;
    private final PromotionService promotionService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final OpenRouterConfig config;

    private static final int SATISFACTION_PROMPT_INTERVAL = 5;

    @org.springframework.beans.factory.annotation.Value("${ai.chat.pass1-timeout-ms:12000}")
    private long pass1TimeoutMs;

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/memm-prompt-template.txt")
    private org.springframework.core.io.Resource promptTemplateResource;

    private String memmPromptTemplate;

    private static final Set<String> STOP_WORDS = Set.of(
            "tôi", "muốn", "ăn", "thừa", "còn", "giúp", "lên", "thực", "đơn", "ở", "nhà", "cho", "với", "hãy", "và", "những", "này", "đang", "cần", "mua", "tìm", "có", "sẵn", "làm", "món", "gì", "được", "không", "thêm", "bớt", "vài", "chút", "ít", "nữa"
    );

    // ── NON-FOOD DENYLIST: product name substrings that are never valid for meal/diet intents ──
    private static final Set<String> NON_FOOD_NAME_DENY = Set.of(
            // Cleaning & Household
            "lau san", "lau bep", "lau kinh", "tay bon cau", "tay trang", "nuoc xit",
            "xit lau", "nuoc giat", "nuoc xa", "bot giat", "xa phong", "choi quet",
            "giay bac", "mang boc", "tui rac", "khan giay", "giay ve sinh",
            "nuoc rua chen", "nuoc rua bat", "vien rua bat", "sap thom",
            "thom phong", "xit phong", "tinh dau phong",
            // Personal care
            "sua tam", "sua rua mat", "dau goi", "dau xa", "kem danh rang",
            "ban chai danh rang", "ban chai", "khan uot", "bao cao su",
            "bang ve sinh", "ta giay", "nuoc hoa", "xit khu mui", "lan khu mui",
            "kem chong nang", "son moi", "phan trang diem",
            // Tools & Hardware
            "pin", "vien pin", "bong den", "day dien", "o cam", "keo dan",
            "bang keo", "but", "viet", "giay a4",
            // Pet
            "thuc an cho", "thuc an meo", "cat ve sinh",
            // Alcohol (for diet intents)
            "ruou vang", "ruou", "bia", "vodka", "whisky",
            // Energy drinks (for diet intents)
            "nuoc tang luc", "red bull", "monster", "sting"
    );

    // ── SEAFOOD DENYLIST: name substrings for hard-blocking when user excludes seafood ──
    private static final Set<String> SEAFOOD_NAME_DENY = Set.of(
            "tom", "cua", "muc", "bach tuoc", "bach tuot", "so diep", "so",
            "hai san", "ca ngu", "ca hoi", "ca basa", "ca tra", "ca chep",
            "ca loc", "ca dieu hong", "ca com", "ca kho", "ca vien", "ca chien",
            "ca ngam", "ca thu", "ca ro", "ca tai tuong", "ca nuc",
            "ngao", "ngheu", "hen", "oc", "ghẹ", "ghe",
            "tom hum", "tom the", "tom su", "tom kho", "tom chien",
            "rong bien", "nuoc mam ca",
            "chao yen hai san", "bun hai san", "lau hai san",
            "shrimp", "crab", "squid", "octopus", "lobster", "scallop",
            "fish", "seafood", "prawn", "clam", "mussel", "oyster"
    );

    private static final Set<String> MEAL_CONTEXT_NAME_DENY = Set.of(
            "ca phe", "coffee", "tra sua", "nuoc ngot", "nuoc tang luc",
            "mayo", "mayonnaise", "sot mayonnaise", "pizza", "ca vien", "bo vien",
            "pho mai que", "nhan pho mai", "snack", "banh keo", "keo", "socola",
            "vang sua", "kem", "bot nghe", "giam tao", "dau an", "dau huong duong",
            "nuoc mam", "tuong ot", "tuong ca", "nuoc tuong", "xiu dau",
            "dau hao", "hat nem", "bot nem", "sua tam", "dau goi"
    );

    private static final Set<String> MEAL_POSITIVE_NAME_TERMS = Set.of(
            "uc ga", "thit ga", "ga", "thit bo", "bo", "thit heo nac", "heo nac",
            "trung", "dau hu", "dau phu", "nam", "yen mach", "bun gao lut",
            "gao lut", "rau", "xa lach", "bap cai", "su hao", "mang tay",
            "bong cai", "ca rot", "bi do", "dua leo", "khoai lang", "tao",
            "oi", "blueberry", "bo 034", "sua hanh nhan", "sua tuoi tach beo"
    );

    private static final Set<String> NON_SEAFOOD_PROTEIN_TERMS = Set.of(
            "uc ga", "thit ga", "ga", "trung", "dau hu", "dau phu",
            "thit bo", "bo", "thit heo nac", "heo nac", "sua tuoi tach beo",
            "sua dau nanh", "dau nanh", "yen mach", "hat"
    );

    @jakarta.annotation.PostConstruct
    public void init() {
        try (java.io.Reader reader = new java.io.InputStreamReader(promptTemplateResource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
            this.memmPromptTemplate = org.springframework.util.FileCopyUtils.copyToString(reader);
        } catch (java.io.IOException e) {
            log.error("Could not load MEMM prompt template", e);
            this.memmPromptTemplate = "BẠN LÀ TRỢ LÝ MUA SẮM THÔNG MINH TẠI SMARTGROCERY.\n\n";
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  MAIN ENTRY POINT
    // ──────────────────────────────────────────────────────────────────────────

    public ChatResponse processChat(Long userId, Long sessionId, String userMessage) {
        userMessage = userMessage == null ? "" : userMessage.trim();
        ChatRequestContext requestContext = prepareChatRequest(userId, sessionId, userMessage);

        if (shouldAskClarificationForBareShoppingList(userMessage, requestContext.getSessionContext())) {
            List<MealOption> existingOptions = readMealOptions(requestContext.getSessionContext());
            ChatResponsePayload payload = existingOptions.isEmpty()
                    ? buildShoppingClarificationPayload()
                    : buildMealSelectionClarificationPayload(existingOptions);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        if (isMealOptionSelectionRequest(userMessage, requestContext.getSessionContext())) {
            ChatResponsePayload payload = buildShoppingListFromSelectedMealPayload(userMessage, requestContext.getSessionContext());
            ensureMutableCollections(payload);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            updateShoppingSessionContext(requestContext.getSessionId(), payload, List.of(), userMessage);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        if (isDirectProductShoppingRequest(userMessage)) {
            ChatResponsePayload payload = buildShoppingListFromDirectProductRequest(userMessage);
            ensureMutableCollections(payload);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            updateShoppingSessionContext(requestContext.getSessionId(), payload, List.of(), userMessage);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        if (isDirectMealShoppingListRequest(userMessage)) {
            ChatResponsePayload payload = buildShoppingListFromDirectMealRequest(userMessage);
            ensureMutableCollections(payload);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            updateShoppingSessionContext(requestContext.getSessionId(), payload, List.of(), userMessage);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        if (isMealIdeaRequest(userMessage)) {
            List<MealOption> options = buildMealOptions(userMessage);
            ChatResponsePayload payload = buildMealOptionsPayload(options);
            updateMealOptionsSessionContext(requestContext.getSessionId(), options, userMessage);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        // 1. Analyze Motivation (Intrinsic + Extrinsic)
        MotivationContext motivation = analyzeMotivation(userId, userMessage);

        // 2. Product Discovery (Search catalog based on message)
        List<ProductNode> discoveredProducts = discoverRelevantProducts(userMessage);
        discoveredProducts = filterAndRankDiscoveredProducts(userMessage, discoveredProducts);
        motivation.setDiscoveredProducts(discoveredProducts);

        // 3. Build MEMM System Prompt & Personalized HCI (AI Inference) ──
        String systemPrompt = buildMemmSystemPrompt(requestContext.getUserName(), requestContext.getInteractionCount(), motivation);

        OpenRouterClient.AiCompletionResult aiResult = null;
        long startTime = System.currentTimeMillis();
        try {
            // Pass 1: Synchronous AI call with 12s timeout to avoid mobile timeout
            aiResult = openRouterClient
                    .chatCompletion(systemPrompt, requestContext.getConversationHistory(), config.getPass1Model(),
                            Duration.ofMillis(Math.max(250, pass1TimeoutMs)))
                    .block();
        } catch (Exception e) {
            log.warn("Pass 1 AI call failed or timed out after {}ms. Falling back to deterministic guard: {}", 
                    (System.currentTimeMillis() - startTime), e.getMessage());
        }

        // STAGE 3: Trust-Building Response (Parse structured output)
        ChatResponsePayload payload = aiResult != null && aiResult.isSuccess()
                ? parseAiResponse(aiResult.getReply())
                : buildFallbackPayload(userMessage, discoveredProducts);
        ensureMutableCollections(payload);

        // ── ENRICHMENT: Specialized Nutrition Logic ──
        if ("MEAL_PLAN_AUTO".equals(payload.getIntentDetected()) && (payload.getProposedItems() == null || payload.getProposedItems().isEmpty())) {
             // If AI wants to generate a meal plan but didn't provide items, trigger the specialized service
             try {
                 NutritionChatIntegrator.MealPlanChatResult mealPlan = nutritionChatIntegrator.generateMealPlanViaChat(userId, userMessage);
                 if (mealPlan.isSuccess()) {
                     payload.setReply(payload.getReply() + "\n\nTôi đã tạo một thực đơn 7 ngày mới cho bạn: " + mealPlan.getTitle());
                     if (mealPlan.getTrustScore() != null) {
                         payload.setTrustScore(mealPlan.getTrustScore().floatValue());
                     }
                     if (mealPlan.getExplanations() != null) {
                         payload.getExplanations().putAll(mealPlan.getExplanations());
                     }
                     // Convert meal plan items to proposed items for the UI/chat
                     for (NutritionChatIntegrator.ProposedItemForChat item : mealPlan.getProposedItems()) {
                         payload.getProposedItems().add(ProposedItemDto.builder()
                                 .productId(item.getProductId())
                                 .quantity(item.getQuantity() != null ? item.getQuantity() : 1)
                                 .reason(item.getReason() != null ? item.getReason() : ("Dành cho bữa " + item.getMealSlot() + " ngày " + item.getDayNo()))
                                 .build());
                     }
                     if (mealPlan.getAllergyWarnings() != null && !mealPlan.getAllergyWarnings().isEmpty()) {
                         payload.setReply(payload.getReply() + "\n" + String.join("\n", mealPlan.getAllergyWarnings()));
                     }
                 }
             } catch (Exception e) {
                 log.warn("Dynamic meal plan generation failed: {}", e.getMessage());
              }
        }

        enforceProposedItemsCandidateScope(payload, userMessage, requestContext.getSessionContext(), discoveredProducts);
        ensureProposedItemsForShoppingAction(payload, userMessage, requestContext.getSessionContext(), discoveredProducts);

        // Filter out pantry staples if not explicitly requested
        if (payload.getProposedItems() != null && !payload.getProposedItems().isEmpty()) {
            enforceRecipeIngredientConsistency(payload, userMessage);
            filterPantryStaples(payload.getProposedItems(), userMessage);
            filterOutOfStock(payload.getProposedItems());
            // ── CONTEXT QUALITY FILTERS ──
            filterNonFoodForMealIntent(payload, userMessage);
            filterExcludedIngredients(payload, userMessage);
            filterLowQualityMealItems(payload, userMessage);
            refillProposedItemsIfTooFew(payload, userMessage);
            syncRecommendedIdsFromProposedItems(payload);
        }
        try {
            filterRecommendedProductIds(payload, userMessage);
        } catch (Exception e) {
            log.warn("filterRecommendedProductIds failed; keeping current validated payload: {}", e.getMessage());
        }
        if (isMealSuggestionOnly(userMessage)) {
            keepSuggestionAsCandidatesOnly(payload);
        }
        enforceReplyConsistency(payload, userMessage);
        updateShoppingSessionContext(requestContext.getSessionId(), payload, discoveredProducts, userMessage);

        // Ã¢â€â‚¬Ã¢â€â‚¬ STAGE 4: Expectation Management Ã¢â€â‚¬Ã¢â€â‚¬
        SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, aiResult);

        // Check if should trigger satisfaction prompt
        boolean shouldAskSatisfaction = saved.getInteractionCount() % SATISFACTION_PROMPT_INTERVAL == 0
                && saved.getInteractionCount() > 0;

        return ChatResponse.builder()
                .sessionId(requestContext.getSessionId())
                .aiMessageId(saved.getMessageId() != null ? String.valueOf(saved.getMessageId()) : null)
                .reply(saved.getFallbackReply())
                .recommendedProductIds(payload.getRecommendedProductIds())
                .proposedItems(payload.getProposedItems())
                .removeVariantIds(payload.getRemoveVariantIds())
                .removeReasons(payload.getRemoveReasons())
                .explanations(payload.getExplanations())
                .trustScore(payload.getTrustScore())
                .thoughtProcess(payload.getThoughtProcess())
                .intentPrediction(payload.getIntentPrediction())
                .replyStatus(saved.getReplyStatus())
                .fallbackReply(saved.getFallbackReply())
                .streamUrl(saved.getMessageId() != null
                        && AiPass2StreamService.STATUS_PENDING_PASS2.equals(saved.getReplyStatus())
                        ? "/api/v1/ai/chat/messages/" + saved.getMessageId() + "/stream"
                        : null)
                .uiActions(buildUiActions(payload))
                .expectationPrompt(shouldAskSatisfaction ? "Gợi ý của AI có hữu ích không? Hãy đánh giá để AI phục vụ bạn tốt hơn!" : null)
                .build();
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    //  STAGE 1: MOTIVATION ANALYSIS
    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    private MotivationContext analyzeMotivation(Long userId, String message) {
        MotivationContext ctx = new MotivationContext();

        // Load nutrition profile (intrinsic: health goals, competence)
        nutritionProfileRepository.findByUser_Id(userId).ifPresent(profile -> {
            ctx.setHealthGoals(profile.getHealthGoals());
            ctx.setAllergies(profile.getAllergies());
            ctx.setDietaryPreference(profile.getDietaryPreference());
            ctx.setBmi(profile.getBmi() != null ? profile.getBmi().floatValue() : null);
        });

        // Cart inspection (extrinsic: current shopping context)
        ctx.setCartContext(nutritionChatIntegrator.analyzeCartForChat(userId));
        try {
            CartInspectionReport report = cartInspectionService.inspectCart(userId);
            ctx.setHasCartConflicts(report.isHasConflicts());
            ctx.setConflictingVariantIds(report.getConflictingVariantIds());
        } catch (Exception e) {
            log.warn("Cart conflict detection failed for user {}: {}", userId, e.getMessage());
        }

        // Fetch promotions
        ctx.setHasPromotions(!promotionService.getAllCampaigns().isEmpty());

        // Inventory & Catalog Summary (Extrinsic Motivation)
        // Note: inventorySummary will be built later using discovered products
        ctx.setInventorySummary(""); 

        return ctx;
    }

    private ChatRequestContext prepareChatRequest(Long userId, Long sessionId, String userMessage) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            ChatSession session;
        if (sessionId != null) {
                session = sessionRepository.findById(sessionId)
                        .orElseGet(() -> createNewSession(user));
            } else {
                session = sessionRepository.findTopByUser_IdAndStatusOrderByLastActiveAtDesc(userId, "ACTIVE")
                        .orElseGet(() -> createNewSession(user));
            }

            ChatMessage userMsg = ChatMessage.builder()
                    .session(session).userId(userId).role("USER").content(userMessage).build();
            messageRepository.save(userMsg);

            List<Map<String, String>> conversationHistory = buildConversationHistory(session.getId());
            return ChatRequestContext.builder()
                    .sessionId(session.getId())
                    .userName(user.getFullName() != null ? user.getFullName() : "Khách hàng")
                    .interactionCount(session.getInteractionCount() != null ? session.getInteractionCount() : 0)
                    .sessionContext(session.getSessionContext())
                    .conversationHistory(conversationHistory)
                    .build();
        }));
    }

    private SavedAssistantMessage saveChatResult(
            Long sessionId,
            Long userId,
            String userMessage,
            ChatResponsePayload payload,
            OpenRouterClient.AiCompletionResult aiResult
    ) {
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> {
            ChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Chat session not found"));
            int nextInteractionCount = (session.getInteractionCount() != null ? session.getInteractionCount() : 0) + 1;
            session.setInteractionCount(nextInteractionCount);
            session.setLastActiveAt(LocalDateTime.now());
            sessionRepository.save(session);

            String fallbackReply = buildFallbackReply(payload);
            String snapshotJson = buildValidatedActionSnapshot(userMessage, payload);

            String replyStatus = (aiResult != null && aiResult.isSuccess()) 
                    ? AiPass2StreamService.STATUS_PENDING_PASS2 
                    : AiPass2StreamService.STATUS_FALLBACK;

            ChatMessage aiMsg = ChatMessage.builder()
                    .session(session).userId(userId).role("ASSISTANT")
                    .content(fallbackReply)
                    .intentDetected(payload.getIntentDetected())
                    .confidenceScore(payload.getTrustScore() != null ? payload.getTrustScore() / 100f : null)
                    .tokensUsed(aiResult != null ? aiResult.getTokensUsed() : 0)
                    .replyStatus(replyStatus)
                    .fallbackReply(fallbackReply)
                    .validatedActionSnapshot(snapshotJson)
                    .build();
            messageRepository.save(aiMsg);
            if (AiPass2StreamService.STATUS_PENDING_PASS2.equals(replyStatus)) {
                eventPublisher.publishEvent(new Pass2RequestedEvent(aiMsg.getId(), userId));
            }

            return SavedAssistantMessage.builder()
                    .messageId(aiMsg.getId())
                    .interactionCount(nextInteractionCount)
                    .fallbackReply(fallbackReply)
                    .replyStatus(replyStatus)
                    .build();
            }));
        } catch (Exception e) {
            log.error("Could not persist AI chat result. Returning fallback response without SSE.", e);
            return SavedAssistantMessage.builder()
                    .messageId(null)
                    .interactionCount(0)
                    .fallbackReply(buildFallbackReply(payload))
                    .replyStatus(AiPass2StreamService.STATUS_FALLBACK)
                    .build();
        }
    }

    private String buildFallbackReply(ChatResponsePayload payload) {
        if (("SHOPPING_LIST_CREATE".equals(payload.getIntentDetected())
                || "DIRECT_MEAL_SHOPPING_LIST".equals(payload.getIntentDetected())
                || "DIRECT_PRODUCT_SHOPPING".equals(payload.getIntentDetected()))
                && payload.getReply() != null && !payload.getReply().isBlank()) {
            return payload.getReply();
        }
        if (payload.getProposedItems() != null && !payload.getProposedItems().isEmpty()) {
            return "Mình đã chuẩn bị danh sách sản phẩm bên dưới cho bạn.";
        }
        if (payload.getReply() != null && !payload.getReply().isBlank()) {
            return payload.getReply();
        }
        return "Mình đã nhận yêu cầu của bạn và đang hoàn tất câu trả lời.";
    }

    private ChatResponse buildImmediateChatResponse(Long sessionId, SavedAssistantMessage saved, ChatResponsePayload payload) {
        return ChatResponse.builder()
                .sessionId(sessionId)
                .aiMessageId(saved.getMessageId() != null ? String.valueOf(saved.getMessageId()) : null)
                .reply(saved.getFallbackReply())
                .recommendedProductIds(payload.getRecommendedProductIds())
                .proposedItems(payload.getProposedItems())
                .removeVariantIds(payload.getRemoveVariantIds())
                .removeReasons(payload.getRemoveReasons())
                .explanations(payload.getExplanations())
                .trustScore(payload.getTrustScore())
                .thoughtProcess(payload.getThoughtProcess())
                .intentPrediction(payload.getIntentPrediction())
                .replyStatus(saved.getReplyStatus())
                .fallbackReply(saved.getFallbackReply())
                .streamUrl(null)
                .uiActions(buildUiActions(payload))
                .expectationPrompt(null)
                .build();
    }

    private String buildValidatedActionSnapshot(String userMessage, ChatResponsePayload payload) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("userMessage", userMessage);
            snapshot.put("intent", payload.getIntentDetected());
            snapshot.put("replyDraft", payload.getReply());
            snapshot.put("recommendedProductIds", payload.getRecommendedProductIds());
            snapshot.put("proposedItems", payload.getProposedItems());
            snapshot.put("removeVariantIds", payload.getRemoveVariantIds());
            snapshot.put("removeReasons", payload.getRemoveReasons());
            snapshot.put("explanations", payload.getExplanations());
            snapshot.put("trustScore", payload.getTrustScore());
            snapshot.put("validatedAt", LocalDateTime.now().toString());
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("Could not serialize validated AI action snapshot: {}", e.getMessage());
            return "{}";
        }
    }

    private List<ProductNode> discoverRelevantProducts(String message) {
        try {
            String cleanMessage = normalizeText(message.replaceAll("\\p{Punct}", " "));
            
            // Check for recipe/fridge rescue intent
            boolean isRecipeIntent = cleanMessage.contains("tu lanh")
                    || cleanMessage.contains("nau")
                    || cleanMessage.contains("mon")
                    || cleanMessage.contains("con");

            String[] words = cleanMessage.split("\\s+");
            String query = Arrays.stream(words)
                    .filter(w -> !w.isBlank() && !STOP_WORDS.contains(w))
                    .collect(Collectors.joining(" "));

            if (query.isBlank()) {
                query = message;
            }

            List<ProductNode> results = productNodeRepository.searchFullText(query);
            
            if (isRecipeIntent && requiresBeef(cleanMessage)) {
                results = new ArrayList<>(results);
                results.addAll(productNodeRepository.searchFullText("bo beef steak thit bo than bo bap bo"));
            } else if (isRecipeIntent && requiresPork(cleanMessage)) {
                results = new ArrayList<>(results);
                results.addAll(productNodeRepository.searchFullText("heo lon pork thit heo nac dam ba roi suon"));
            } else if (isRecipeIntent && requiresChicken(cleanMessage)) {
                results = new ArrayList<>(results);
                results.addAll(productNodeRepository.searchFullText("ga chicken thit ga uc ga dui ga"));
            }

            // If it's a recipe intent, proactively fetch common ingredients to avoid hallucination
            if (isRecipeIntent && !requiresBeef(cleanMessage) && !requiresPork(cleanMessage) && !requiresChicken(cleanMessage)) {
                results = new ArrayList<>(results);
                // Search for common protein sources to give AI real options
                results.addAll(productNodeRepository.searchFullText("thit ca trung dau"));
            }

            return results;
        } catch (Exception e) {
            log.warn("Product discovery failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ProductNode> filterAndRankDiscoveredProducts(String userMessage, List<ProductNode> discovered) {
        if (!isMealOrDietIntent(userMessage)) {
            return discovered == null ? List.of() : discovered;
        }

        LinkedHashMap<Long, ProductNode> candidates = new LinkedHashMap<>();
        if (discovered != null) {
            for (ProductNode node : discovered) {
                if (node != null && node.getProductId() != null && node.getProductId() > 0) {
                    candidates.putIfAbsent(node.getProductId(), node);
                }
            }
        }

        for (Product product : deterministicMealFallbackProducts(userMessage)) {
            candidates.putIfAbsent(product.getId(), toProductNode(product));
        }

        return candidates.values().stream()
                .filter(node -> isMealCandidateAllowed(userMessage, node.getName(), node.getCategoryName(), node.getDescription()))
                .sorted(Comparator.comparingInt((ProductNode node) ->
                        mealCandidateScore(userMessage, node.getName(), node.getCategoryName(), node.getDescription())).reversed())
                .limit(24)
                .toList();
    }

    private List<Product> deterministicMealFallbackProducts(String userMessage) {
        try {
            return productRepository.findActiveWithCategory().stream()
                    .filter(product -> isMealCandidateAllowed(
                            userMessage,
                            product.getName(),
                            product.getCategory() != null ? product.getCategory().getName() : "",
                            product.getDescription()
                    ))
                    .sorted(Comparator.comparingInt((Product product) ->
                            mealCandidateScore(
                                    userMessage,
                                    product.getName(),
                                    product.getCategory() != null ? product.getCategory().getName() : "",
                                    product.getDescription()
                            )).reversed())
                    .limit(30)
                    .toList();
        } catch (Exception e) {
            log.warn("Deterministic meal fallback discovery failed: {}", e.getMessage());
            return List.of();
        }
    }

    private ProductNode toProductNode(Product product) {
        return ProductNode.builder()
                .productId(product.getId())
                .name(product.getName())
                .productCode(product.getProductCode())
                .status(product.getStatus())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .isStaple(product.getIsStaple())
                .build();
    }

    private boolean isMealCandidateAllowed(String userMessage, String name, String categoryName, String description) {
        boolean explicitlyRequested = explicitlyRequestsProduct(userMessage, name);
        String haystack = normalizeText(String.join(" ",
                name != null ? name : "",
                categoryName != null ? categoryName : "",
                description != null ? description : ""
        ));
        if (haystack.isBlank()) {
            return false;
        }
        for (String deny : NON_FOOD_NAME_DENY) {
            if (haystack.contains(deny)) {
                return explicitlyRequested && !isMealOrDietIntent(userMessage);
            }
        }
        if (!explicitlyRequested) {
            for (String deny : MEAL_CONTEXT_NAME_DENY) {
                if (haystack.contains(deny)) {
                    return false;
                }
            }
        }
        if (excludesSeafood(userMessage)) {
            for (String deny : SEAFOOD_NAME_DENY) {
                if (haystack.contains(deny)) {
                    return false;
                }
            }
        }
        return explicitlyRequested || mealCandidateScore(userMessage, name, categoryName, description) > 0;
    }

    private int mealCandidateScore(String userMessage, String name, String categoryName, String description) {
        String n = normalizeText(userMessage);
        String haystack = normalizeText(String.join(" ",
                name != null ? name : "",
                categoryName != null ? categoryName : "",
                description != null ? description : ""
        ));
        int score = 0;
        for (String term : MEAL_POSITIVE_NAME_TERMS) {
            if (haystack.contains(term)) {
                score += 8;
            }
        }
        if (haystack.contains("rau") || haystack.contains("cu qua") || haystack.contains("trai cay")) score += 8;
        if (haystack.contains("thit") || haystack.contains("trung") || haystack.contains("dau hu") || haystack.contains("protein")) score += 10;
        if (haystack.contains("yen mach") || haystack.contains("gao lut") || haystack.contains("bun gao lut") || haystack.contains("khoai lang")) score += 6;
        if (n.contains("giam can") || n.contains("healthy") || n.contains("nhe") || n.contains("khong ngay")) {
            if (haystack.contains("rau") || haystack.contains("uc ga") || haystack.contains("dau hu")
                    || haystack.contains("yen mach") || haystack.contains("gao lut") || haystack.contains("trai cay")) {
                score += 10;
            }
        }
        if (n.contains("protein") || n.contains("no lau")) {
            if (haystack.contains("uc ga") || haystack.contains("thit") || haystack.contains("trung")
                    || haystack.contains("dau hu") || haystack.contains("dau phu") || haystack.contains("yen mach")) {
                score += 12;
            }
            for (String term : NON_SEAFOOD_PROTEIN_TERMS) {
                if (haystack.contains(term)) {
                    score += 10;
                }
            }
        }
        if (n.contains("bua sang") || n.contains("an sang")) {
            if (haystack.contains("yen mach") || haystack.contains("sua hanh nhan") || haystack.contains("sua tuoi tach beo")
                    || haystack.contains("trung") || haystack.contains("tao") || haystack.contains("blueberry")) {
                score += 12;
            }
        }
        if (n.contains("bua toi") || n.contains("an toi")) {
            if (haystack.contains("uc ga") || haystack.contains("dau hu") || haystack.contains("rau")
                    || haystack.contains("mang tay") || haystack.contains("su hao") || haystack.contains("bun gao lut")) {
                score += 12;
            }
        }
        return score;
    }

    private boolean explicitlyRequestsProduct(String userMessage, String productName) {
        String message = normalizeText(userMessage);
        String name = normalizeText(productName);
        if (message.isBlank() || name.isBlank()) {
            return false;
        }
        if (message.contains(name)) {
            return true;
        }
        List<String> tokens = Arrays.stream(name.split("\\s+"))
                .filter(token -> token.length() >= 2)
                .toList();
        for (int i = 0; i + 1 < tokens.size(); i++) {
            String phrase = tokens.get(i) + " " + tokens.get(i + 1);
            if (message.contains(phrase)) {
                return true;
            }
        }
        for (String requestablePhrase : MEAL_CONTEXT_NAME_DENY) {
            if (requestablePhrase.length() >= 5 && message.contains(requestablePhrase) && name.contains(requestablePhrase)) {
                return true;
            }
        }
        return false;
    }

    private String buildInventorySummary(List<ProductNode> discovered) {
        try {
            List<Product> stapleProducts = productRepository.findTop15ByStatusAndIsStapleTrueOrderByIdAsc("ACTIVE");
            List<Product> catalogSample = productRepository.findTop20ByStatusOrderByIdAsc("ACTIVE");
            LinkedHashSet<Long> summaryProductIds = new LinkedHashSet<>();
            if (discovered != null) {
                discovered.stream()
                        .map(ProductNode::getProductId)
                        .filter(Objects::nonNull)
                        .filter(id -> id > 0)
                        .forEach(summaryProductIds::add);
            }
            boolean includeGenericCatalog = summaryProductIds.isEmpty();
            if (includeGenericCatalog) {
                stapleProducts.stream().map(Product::getId).forEach(summaryProductIds::add);
                catalogSample.stream().map(Product::getId).forEach(summaryProductIds::add);
            }
            Map<Long, ProductInfo> stockMap = buildProductInfoMap(new ArrayList<>(summaryProductIds));

            StringBuilder summary = new StringBuilder();
            if (discovered != null && !discovered.isEmpty()) {
                summary.append("SAN PHAM LIEN QUAN DEN CAU HOI:\n");
                for (ProductNode p : discovered) {
                    if (p.getProductId() == null || p.getProductId() <= 0) {
                        continue;
                    }
                    ProductInfo info = stockMap.getOrDefault(p.getProductId(), new ProductInfo(0, "sản phẩm"));
                    String desc = (p.getDescription() != null && !p.getDescription().isBlank()) 
                            ? " | Mo ta: " + p.getDescription() : "";
                    String stockStr = info.stock > 0 ? String.valueOf(info.stock) : "0 (HET HANG)";
                    summary.append(String.format("- ID:%d | %s | Gia: %sd | Don vi: %s | Ton kho: %s%s\n",
                        p.getProductId(), p.getName(), p.getPrice(), info.unit, stockStr, desc));
                }
            }

            if (includeGenericCatalog) {
            summary.append("\nNHU YEU PHAM & GIA VI:\n");
            stapleProducts.forEach(p -> {
                        ProductInfo info = stockMap.getOrDefault(p.getId(), new ProductInfo(0, "sản phẩm"));
                        String stockStr = info.stock > 0 ? String.valueOf(info.stock) : "0 (HET HANG)";
                        summary.append(String.format("- ID:%d | %s | Don vi: %s | Ton kho: %s\n",
                            p.getId(), p.getName(), info.unit, stockStr));
                    });
            
            summary.append("\nDANH MUC KHAC:\n");
            Set<Long> discoveredIds = discovered == null
                    ? Set.of()
                    : discovered.stream()
                            .map(ProductNode::getProductId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
            catalogSample.stream()
                    .filter(p -> !discoveredIds.contains(p.getId()))
                    .forEach(p -> {
                        ProductInfo info = stockMap.getOrDefault(p.getId(), new ProductInfo(0, "sản phẩm"));
                        String stockStr = info.stock > 0 ? String.valueOf(info.stock) : "0 (HET HANG)";
                        summary.append(String.format("- ID:%d | %s | Don vi: %s | Ton kho: %s\n",
                            p.getId(), p.getName(), info.unit, stockStr));
                    });

            }

            summary.append("\nLUU Y QUAN TRONG: San pham ton kho = 0 thi khong dua vao proposedItems.\n");
            return summary.toString();
        } catch (Exception e) {
            log.warn("Failed to build inventory summary: {}", e.getMessage());
            return "Dữ liệu kho hàng tạm thời không khả dụng.";
        }
    }

    private record ProductInfo(int stock, String unit) {}

    private Map<Long, ProductInfo> buildProductInfoMap(List<Long> productIds) {
        Map<Long, ProductInfo> infoMap = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) return infoMap;
        try {
            // Batch fetch all variants for the given product IDs
            List<ProductVariant> allVariants = productVariantRepository.findByProductIdsAndStatusWithProduct(productIds, "ACTIVE").stream()
                    .filter(this::isActiveVariantForActiveProduct)
                    .toList();
            Map<Long, List<ProductVariant>> productToVariants = allVariants.stream()
                    .filter(v -> v.getProduct() != null)
                    .collect(Collectors.groupingBy(v -> v.getProduct().getId()));
            
            List<Long> allVariantIds = allVariants.stream().map(ProductVariant::getId).toList();
            if (!allVariantIds.isEmpty()) {
                List<InventoryStockRepository.VariantStockSum> stockSums = 
                        inventoryStockRepository.sumAvailableByVariantIds(allVariantIds);
                Map<Long, Long> variantStockMap = stockSums.stream()
                        .collect(Collectors.toMap(
                                InventoryStockRepository.VariantStockSum::getVariantId,
                                InventoryStockRepository.VariantStockSum::getTotalAvailable
                        ));
                
                for (Map.Entry<Long, List<ProductVariant>> entry : productToVariants.entrySet()) {
                    int totalStock = entry.getValue().stream()
                            .mapToInt(v -> variantStockMap.getOrDefault(v.getId(), 0L).intValue())
                            .sum();
                    // Take unit from first variant
                    String unit = entry.getValue().isEmpty() ? "sản phẩm" : entry.getValue().get(0).getUnit();
                    infoMap.put(entry.getKey(), new ProductInfo(totalStock, unit));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to build product info map: {}", e.getMessage());
        }
        return infoMap;
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    //  STAGE 2: BUILD MEMM SYSTEM PROMPT
    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    private String buildMemmSystemPrompt(String userName, int interactionCount, MotivationContext ctx) {
        if (this.memmPromptTemplate == null) {
            return "ERROR: PROMPT TEMPLATE NOT LOADED";
        }

        return this.memmPromptTemplate
                .replace("{{USER_NAME}}", userName != null ? userName : "Khách hàng")
                .replace("{{INTERACTION_COUNT}}", String.valueOf(interactionCount))
                .replace("{{BMI}}", ctx.getBmi() != null ? String.valueOf(ctx.getBmi()) : "Không rõ")
                .replace("{{HEALTH_GOALS}}", ctx.getHealthGoals() != null ? ctx.getHealthGoals() : "Không có")
                .replace("{{ALLERGIES}}", ctx.getAllergies() != null ? ctx.getAllergies() : "Không có")
                .replace("{{INVENTORY_SUMMARY}}", buildInventorySummary(ctx.getDiscoveredProducts()))
                .replace("{{CART_CONTEXT}}", ctx.getCartContext() != null ? ctx.getCartContext() : "Trống");
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    //  STAGE 3: PARSE TRUST-BUILDING RESPONSE
    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    private ChatResponsePayload parseAiResponse(String aiReply) {
        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setReply(aiReply);
        payload.setIntentDetected("CHAT");

        if (aiReply == null || aiReply.isBlank()) {
            payload.setReply("Xin lỗi, tôi chưa hiểu yêu cầu. Bạn có thể nói rõ hơn không?");
            return payload;
        }

        // Try to extract JSON from response
        String jsonStr = extractJson(aiReply);
        if (jsonStr == null) {
            // Response is plain text Ã¢â‚¬â€ still clean IDs before returning
            payload.setReply(stripProductIds(aiReply));
            return payload;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonStr);

            String rawReply = root.path("reply").asText(aiReply);
            payload.setReply(stripProductIds(rawReply));
            payload.setIntentDetected(root.path("intentDetected").asText("CHAT"));
            payload.setTrustScore(root.has("trustScore") ? (float) root.path("trustScore").asDouble(75) : null);
            String thought = root.path("thoughtProcess").asText("");
            payload.setThoughtProcess(thought);
            
            if (!thought.isBlank()) {
                log.debug("AI thought process captured ({} chars). See ai-debug.txt for details.", thought.length());

                // Keep verbose AI reasoning out of console logs; write it as UTF-8 for debugging.
                try {
                    String logEntry = String.format("\n[%s] AI THOUGHT:\n%s\n%s\n",
                            java.time.LocalDateTime.now(), thought, "=".repeat(30));
                    java.nio.file.Files.write(
                            java.nio.file.Paths.get("ai-debug.txt"),
                            logEntry.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND
                    );
                } catch (Exception e) {
                    log.warn("Failed to write AI thought to file: {}", e.getMessage());
                }
            }

            // Recommended products
            if (root.has("recommendedProductIds") && root.get("recommendedProductIds").isArray()) {
                List<Long> ids = new ArrayList<>();
                root.get("recommendedProductIds").forEach(n -> parsePositiveLong(n).ifPresent(ids::add));
                payload.setRecommendedProductIds(ids);
            }

            // Proposed items
            if (root.has("proposedItems") && root.get("proposedItems").isArray()) {
                List<ProposedItemDto> items = new ArrayList<>();
                for (JsonNode item : root.get("proposedItems")) {
                    Optional<Long> productId = parsePositiveLong(item.get("productId"));
                    if (productId.isEmpty()) {
                        continue;
                    }
                    // Clamp quantity to valid integer range [1, 999]
                    int rawQty = item.path("quantity").asInt(1);
                    int clampedQty = Math.max(1, Math.min(999, rawQty));
                    items.add(ProposedItemDto.builder()
                            .productId(productId.get())
                            .quantity(clampedQty)
                            .reason(item.path("reason").asText(""))
                            .build());
                }
                payload.setProposedItems(items);
            }

            // Remove variant IDs (cart conflicts)
            if (root.has("removeVariantIds") && root.get("removeVariantIds").isArray()) {
                List<Long> removeIds = new ArrayList<>();
                root.get("removeVariantIds").forEach(n -> {
                    if (n.isNumber()) removeIds.add(n.asLong());
                    else if (n.isTextual()) {
                        try { removeIds.add(Long.parseLong(n.asText())); } catch (Exception ignored) {}
                    }
                });
                payload.setRemoveVariantIds(removeIds);
            }

            // Remove reasons
            if (root.has("removeReasons") && root.get("removeReasons").isObject()) {
                Map<Long, String> reasons = new HashMap<>();
                root.get("removeReasons").fields().forEachRemaining(entry -> {
                    try {
                        long id = Long.parseLong(entry.getKey());
                        reasons.put(id, entry.getValue().asText());
                    } catch (NumberFormatException e) {
                        log.warn("AI returned non-numeric key in removeReasons: {}", entry.getKey());
                    }
                });
                payload.setRemoveReasons(reasons);
            }

            // Intent Prediction
            if (root.has("intent_prediction") && root.get("intent_prediction").isObject()) {
                JsonNode ipNode = root.get("intent_prediction");
                IntentPredictionDto ipDto = new IntentPredictionDto();
                ipDto.setDetectedIntent(ipNode.path("detected_intent").asText(null));
                ipDto.setMessage(ipNode.path("message").asText(null));
                ipDto.setBundleActionUi(ipNode.path("bundle_action_ui").asText(null));

                if (ipNode.has("smart_suggestions") && ipNode.get("smart_suggestions").isArray()) {
                    List<SmartSuggestionDto> suggestions = new ArrayList<>();
                    for (JsonNode item : ipNode.get("smart_suggestions")) {
                        suggestions.add(SmartSuggestionDto.builder()
                                .itemId(item.path("item_id").asLong())
                                .itemName(item.path("item_name").asText(null))
                                .actionUi(item.path("action_ui").asText(null))
                                .build());
                    }
                    ipDto.setSmartSuggestions(suggestions);
                }
                payload.setIntentPrediction(ipDto);
            }

            // Explanations
            if (root.has("explanations") && root.get("explanations").isObject()) {
                Map<Long, String> explanations = new HashMap<>();
                root.get("explanations").fields().forEachRemaining(entry ->
                        explanations.put(Long.parseLong(entry.getKey()), entry.getValue().asText()));
                payload.setExplanations(explanations);
            }

        } catch (Exception e) {
            log.warn("Failed to parse structured AI response, using raw text: {}", e.getMessage());
            payload.setProposedItems(new ArrayList<>());
            payload.setRecommendedProductIds(new ArrayList<>());
            payload.setRemoveVariantIds(new ArrayList<>());
            payload.setRemoveReasons(new HashMap<>());
            payload.setExplanations(new HashMap<>());
            payload.setIntentPrediction(null);
            payload.setReply("Xin lỗi, tôi vừa gặp lỗi kỹ thuật trong quá trình xử lý yêu cầu. Vui lòng thử lại sau.");
        }

        return payload;
    }
    private ChatResponsePayload buildFallbackPayload(String userMessage, List<ProductNode> discoveredProducts) {
        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setIntentDetected("CHAT");
        payload.setTrustScore(60f); // Higher trust for deterministic fallback
        payload.setReply("Mình đã chuẩn bị danh sách mua sắm phù hợp nhất từ các sản phẩm hiện có trong cửa hàng nhé.");

        if (discoveredProducts == null || discoveredProducts.isEmpty()) {
            return payload;
        }

        List<Long> candidateIds = discoveredProducts.stream()
                .map(ProductNode::getProductId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(12)
                .toList();
        Set<Long> stockedProductIds = findActiveStockedProductIds(candidateIds);
        List<Long> validIds = candidateIds.stream()
                .filter(stockedProductIds::contains)
                .toList();

        payload.setRecommendedProductIds(new ArrayList<>(validIds));
        if (isShoppingListRequest(userMessage)) {
            List<ProposedItemDto> fallbackItems = validIds.stream()
                    .map(productId -> ProposedItemDto.builder()
                            .productId(productId)
                            .quantity(1)
                            .reason("Gợi ý phù hợp nhất từ danh mục sản phẩm hiện có.")
                            .build())
                    .toList();
            payload.setProposedItems(new ArrayList<>(fallbackItems));
        }
        return payload;
    }

    private ChatResponsePayload buildShoppingClarificationPayload() {
        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setIntentDetected("SHOPPING_LIST_CLARIFICATION");
        payload.setTrustScore(75f);
        payload.setReply("Bạn muốn mình tạo danh sách cho mục tiêu nào? Ví dụ: bữa tối giảm cân, bữa sáng healthy, hoặc danh sách giàu protein.");
        payload.setRecommendedProductIds(new ArrayList<>());
        payload.setProposedItems(new ArrayList<>());
        payload.setRemoveVariantIds(new ArrayList<>());
        payload.setRemoveReasons(new HashMap<>());
        payload.setExplanations(new HashMap<>());
        return payload;
    }

    private ChatResponsePayload buildMealSelectionClarificationPayload(List<MealOption> options) {
        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setIntentDetected("MEAL_SELECTION_CLARIFICATION");
        payload.setTrustScore(78f);
        payload.setRecommendedProductIds(new ArrayList<>());
        payload.setProposedItems(new ArrayList<>());
        payload.setRemoveVariantIds(new ArrayList<>());
        payload.setRemoveReasons(new HashMap<>());
        payload.setExplanations(new HashMap<>());
        String choices = options.stream()
                .map(option -> option.getOptionNo() + ". " + option.getTitle())
                .collect(Collectors.joining("\n"));
        payload.setReply("Mình có các thực đơn này trong phiên hiện tại:\n\n"
                + choices
                + "\n\nBạn chọn số mấy để mình tạo danh sách mua sắm?");
        return payload;
    }

    private ChatResponsePayload buildMealOptionsPayload(List<MealOption> options) {
        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setIntentDetected("MEAL_OPTIONS");
        payload.setTrustScore(82f);
        payload.setRecommendedProductIds(new ArrayList<>());
        payload.setProposedItems(new ArrayList<>());
        payload.setRemoveVariantIds(new ArrayList<>());
        payload.setRemoveReasons(new HashMap<>());
        payload.setExplanations(new HashMap<>());

        StringBuilder reply = new StringBuilder("Mình gợi ý vài thực đơn để bạn chọn nhé:\n\n");
        for (MealOption option : options) {
            reply.append(option.getOptionNo()).append(". ").append(option.getTitle()).append("\n")
                    .append("Nguyên liệu chính: ").append(String.join(", ", option.getIngredients())).append(".\n")
                    .append("Phù hợp: ").append(option.getReason()).append(".\n\n");
        }
        reply.append("Bạn muốn chọn thực đơn số mấy để mình tạo danh sách mua sắm?");
        payload.setReply(reply.toString().trim());
        return payload;
    }

    private List<MealOption> buildMealOptions(String userMessage) {
        String n = normalizeText(userMessage);
        if (n.contains("bua sang") || n.contains("an sang")) {
            return List.of(
                    MealOption.builder()
                            .optionNo(1)
                            .title("Yến mạch sữa tươi tách béo + blueberry")
                            .ingredients(List.of("yến mạch", "sữa tươi tách béo", "blueberry"))
                            .reason("nhẹ bụng, nhiều chất xơ, chuẩn bị nhanh")
                            .build(),
                    MealOption.builder()
                            .optionNo(2)
                            .title("Trứng luộc + táo + sữa hạnh nhân không đường")
                            .ingredients(List.of("trứng", "táo", "sữa hạnh nhân"))
                            .reason("giàu protein, ít đường, dễ kiểm soát khẩu phần")
                            .build(),
                    MealOption.builder()
                            .optionNo(3)
                            .title("Khoai lang + trứng + dưa leo")
                            .ingredients(List.of("khoai lang", "trứng", "dưa leo"))
                            .reason("no lâu, ít dầu mỡ, hợp bữa sáng healthy")
                            .build()
            );
        }

        if (n.contains("an chay")) {
            return List.of(
                    MealOption.builder()
                            .optionNo(1)
                            .title("Đậu hũ sốt nấm + su hào luộc")
                            .ingredients(List.of("đậu hũ", "nấm", "su hào"))
                            .reason("protein thực vật, ít calo, dễ ăn tối")
                            .build(),
                    MealOption.builder()
                            .optionNo(2)
                            .title("Salad rau xanh + khoai lang + sữa hạnh nhân")
                            .ingredients(List.of("xà lách", "dưa leo", "khoai lang", "sữa hạnh nhân"))
                            .reason("nhiều chất xơ, tinh bột tốt, nhẹ bụng")
                            .build(),
                    MealOption.builder()
                            .optionNo(3)
                            .title("Yến mạch mặn + nấm + rau xanh")
                            .ingredients(List.of("yến mạch", "nấm", "rau xanh"))
                            .reason("ấm bụng, ít dầu, phù hợp giảm cân")
                            .build()
            );
        }

        return List.of(
                MealOption.builder()
                        .optionNo(1)
                        .title("Ức gà áp chảo + măng tây + khoai lang")
                        .ingredients(List.of("ức gà", "măng tây", "khoai lang"))
                        .reason("giàu protein, no lâu, ít dầu mỡ")
                        .build(),
                MealOption.builder()
                        .optionNo(2)
                        .title("Đậu hũ sốt nấm + su hào luộc + yến mạch mặn")
                        .ingredients(List.of("đậu hũ", "nấm", "su hào", "yến mạch"))
                        .reason("nhẹ bụng, giàu chất xơ, hợp bữa tối giảm cân")
                        .build(),
                MealOption.builder()
                        .optionNo(3)
                        .title("Trứng luộc + salad rau xanh + táo")
                        .ingredients(List.of("trứng", "xà lách", "dưa leo", "táo"))
                        .reason("đơn giản, ít calo, dễ chuẩn bị")
                        .build()
        );
    }

    /**
     * Strips all product-ID artifacts from AI reply text.
     * Since IDs belong ONLY in the JSON proposedItems array,
     * we aggressively remove every trace of IDs from the visible text.
     */
    private String stripProductIds(String text) {
        if (text == null || text.isBlank()) return text;

        String working = text;

        // 1. Remove Markdown-style product links: [**Name**](product:123) Ã¢â€ â€™ **Name**
        working = working.replaceAll("\\[([^\\]]*?)\\]\\(product:\\d+\\)", "$1");

        // 2. Remove standalone product link fragments: (product:123)
        working = working.replaceAll("\\(product:\\d+\\)", "");

        // 3. Remove explicit ID labels: "ID: 123", "Ma: 45", "Ma san pham: 789"
        working = working.replaceAll("(?i)\\b(id|mã\\s*sản\\s*phẩm|mã|ma\\s*san\\s*pham|ma|product\\s*id)\\s*[:\\-]?\\s*\\d+", "");

        // 4. Remove pipe-delimited ID patterns from inventory echo: "ID:123 |"
        working = working.replaceAll("(?i)ID:\\d+\\s*\\|?", "");

        // 5. Remove bare IDs in parentheses/brackets: "(123)", "[45]"
        working = working.replaceAll("[\\(\\[]\\s*\\d+\\s*[\\)\\]]", "");

        // 6. Remove empty parentheses/brackets left behind: "()", "( )", "[]"
        working = working.replaceAll("[\\(\\[]\\s*[\\)\\]]", "");

        // 7. Collapse multiple whitespace and stray pipes
        working = working.replaceAll("\\s{2,}", " ")
                         .replaceAll("\\|\\s*\\|", "|")
                         .replaceAll("\\s*\\|\\s*$", "")
                         .trim();

        return working;
    }

    private void ensureProposedItemsForShoppingAction(
            ChatResponsePayload payload,
            String userMessage,
            String sessionContext,
            List<ProductNode> discoveredProducts
    ) {
        if (payload.getProposedItems() != null && !payload.getProposedItems().isEmpty()) {
            return;
        }
        if (!isShoppingListRequest(userMessage)) {
            return;
        }

        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();
        if (!isModificationRequest(userMessage)) {
            readShoppingCandidateIds(sessionContext).forEach(candidateIds::add);
        }
        if (payload.getRecommendedProductIds() != null) {
            payload.getRecommendedProductIds().stream()
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .forEach(candidateIds::add);
        }
        if (discoveredProducts != null) {
            discoveredProducts.stream()
                    .map(ProductNode::getProductId)
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .forEach(candidateIds::add);
        }

        if (candidateIds.isEmpty()) {
            // No candidates at all → ask clarification instead of random list
            if (payload.getReply() == null || payload.getReply().isBlank()
                    || payload.getReply().contains("danh sách") || payload.getReply().contains("chuẩn bị")) {
                payload.setReply("Bạn muốn mình tạo danh sách cho mục tiêu nào? " +
                        "Ví dụ: bữa tối giảm cân, bữa sáng healthy, hoặc danh sách giàu protein.");
            }
            return;
        }

        Set<Long> stockedProductIds = findActiveStockedProductIds(candidateIds);
        if (stockedProductIds.isEmpty()) {
            return;
        }

        List<ProposedItemDto> fallbackItems = candidateIds.stream()
                .filter(stockedProductIds::contains)
                .limit(12)
                .map(productId -> ProposedItemDto.builder()
                        .productId(productId)
                        .quantity(1)
                        .reason("Added from the confirmed shopping list request.")
                        .build())
                .toList();

        payload.setProposedItems(new ArrayList<>(fallbackItems));
        payload.setRecommendedProductIds(new ArrayList<>(fallbackItems.stream()
                .map(ProposedItemDto::getProductId)
                .toList()));
        if (readLastShoppingCandidateMealIntent(sessionContext)) {
            filterLowQualityMealItems(payload, "bua an healthy protein no lau");
            syncRecommendedIdsFromProposedItems(payload);
        }
        log.info("AI Shopping Guard: generated {} proposedItems from structured IDs", fallbackItems.size());
    }

    private void enforceProposedItemsCandidateScope(
            ChatResponsePayload payload,
            String userMessage,
            String sessionContext,
            List<ProductNode> discoveredProducts
    ) {
        if (payload.getProposedItems() == null || payload.getProposedItems().isEmpty()) {
            return;
        }

        Set<Long> allowedIds = buildAllowedShoppingCandidateIds(payload, sessionContext, discoveredProducts);
        if (allowedIds.isEmpty()) {
            payload.setProposedItems(new ArrayList<>());
            payload.setRecommendedProductIds(new ArrayList<>());
            return;
        }

        int before = payload.getProposedItems().size();
        payload.getProposedItems().removeIf(item -> item.getProductId() == null || !allowedIds.contains(item.getProductId()));
        if (payload.getRecommendedProductIds() != null) {
            payload.getRecommendedProductIds().removeIf(id -> id == null || !allowedIds.contains(id));
        }

        if (payload.getProposedItems().size() < before) {
            log.info("AI Shopping Guard: removed {} proposedItems outside backend candidate scope",
                    before - payload.getProposedItems().size());
        }

        if (isModificationRequest(userMessage) && payload.getProposedItems().isEmpty()) {
            appendCorrection(payload, "Minh can xac nhan lai danh sach sau khi thay doi de tranh them sai san pham vao gio.");
        }
    }

    private Set<Long> buildAllowedShoppingCandidateIds(
            ChatResponsePayload payload,
            String sessionContext,
            List<ProductNode> discoveredProducts
    ) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        readShoppingCandidateIds(sessionContext).forEach(ids::add);
        if (payload.getRecommendedProductIds() != null) {
            payload.getRecommendedProductIds().stream()
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .forEach(ids::add);
        }
        if (discoveredProducts != null) {
            discoveredProducts.stream()
                    .map(ProductNode::getProductId)
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .forEach(ids::add);
        }
        return findActiveStockedProductIds(ids);
    }

    private boolean isShoppingListRequest(String userMessage) {
        String n = normalizeText(userMessage);
        return n.contains("tao danh sach")
                || n.contains("lap danh sach")
                || n.contains("chot danh sach")
                || n.contains("danh sach mua")
                || n.contains("shopping list")
                || n.contains("list mua")
                || n.contains("mua sam")
                || n.contains("mua do")
                || n.contains("mua nguyen lieu")
                || n.contains("them vao gio")
                || n.contains("bo vao gio")
                || n.contains("cho vao gio")
                || n.contains("them tat ca")
                || n.contains("chot mon")
                || n.contains("chot bua");
    }

    private boolean isDirectMealShoppingListRequest(String userMessage) {
        String n = normalizeText(userMessage);
        if (!isShoppingListRequest(userMessage)) {
            return false;
        }
        return n.contains("mon ")
                || n.contains("nguyen lieu")
                || !extractDirectIngredientTerms(userMessage).isEmpty();
    }

    private boolean isDirectProductShoppingRequest(String userMessage) {
        String n = normalizeText(userMessage);
        if (!isShoppingListRequest(userMessage)) {
            return false;
        }
        if (isMealOrDietIntent(userMessage) || isMealIdeaRequest(userMessage)) {
            return false;
        }
        if (n.contains("mon ") || n.contains("nguyen lieu")) {
            return false;
        }
        return !findExplicitlyRequestedProducts(userMessage).isEmpty();
    }

    private boolean isMealIdeaRequest(String userMessage) {
        String n = normalizeText(userMessage);
        if (!isMealOrDietIntent(userMessage) || isMealOptionSelectionRequest(userMessage, null)) {
            return false;
        }
        if (n.contains("danh sach mua") || n.contains("mua sam")
                || n.contains("them vao gio") || n.contains("cho vao gio") || n.contains("bo vao gio")) {
            return false;
        }
        return n.contains("goi y")
                || n.contains("suggest")
                || n.contains("tao bua")
                || n.contains("tao mon")
                || n.contains("tao thuc don")
                || n.contains("len thuc don")
                || n.contains("an gi")
                || n.contains("bua toi")
                || n.contains("bua sang")
                || n.contains("bua trua")
                || n.contains("an toi")
                || n.contains("an sang")
                || n.contains("an trua");
    }

    private boolean isMealOptionSelectionRequest(String userMessage, String sessionContext) {
        if (parseSelectedMealOptionNo(userMessage).isEmpty()) {
            return false;
        }
        if (sessionContext != null && readMealOptions(sessionContext).isEmpty()) {
            return false;
        }
        String n = normalizeText(userMessage);
        return n.matches("\\d+")
                || n.contains("chon")
                || n.contains("lay")
                || n.contains("thuc don")
                || n.contains("mon")
                || n.contains("so ")
                || n.contains("option")
                || isShoppingListRequest(userMessage);
    }

    private OptionalInt parseSelectedMealOptionNo(String userMessage) {
        String n = normalizeText(userMessage);
        if (n.isBlank()) {
            return OptionalInt.empty();
        }
        for (int optionNo = 1; optionNo <= 5; optionNo++) {
            String value = String.valueOf(optionNo);
            if (n.equals(value)
                    || n.contains("so " + value)
                    || n.contains("mon " + value)
                    || n.contains("mon so " + value)
                    || n.contains("thuc don " + value)
                    || n.contains("thuc don so " + value)
                    || n.contains("option " + value)
                    || n.contains("chon " + value)
                    || n.contains("lay " + value)) {
                return OptionalInt.of(optionNo);
            }
        }
        return OptionalInt.empty();
    }

    private ChatResponsePayload buildShoppingListFromDirectProductRequest(String userMessage) {
        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setIntentDetected("DIRECT_PRODUCT_SHOPPING");
        payload.setTrustScore(85f);
        payload.setRecommendedProductIds(new ArrayList<>());
        payload.setProposedItems(new ArrayList<>());
        payload.setRemoveVariantIds(new ArrayList<>());
        payload.setRemoveReasons(new HashMap<>());
        payload.setExplanations(new HashMap<>());

        List<Product> matchedProducts = findExplicitlyRequestedProducts(userMessage);
        if (matchedProducts.isEmpty()) {
            payload.setTrustScore(65f);
            payload.setReply("Mình chưa tìm thấy sản phẩm khớp rõ với yêu cầu của bạn. Bạn có thể nói rõ tên sản phẩm hơn không?");
            return payload;
        }

        Set<Long> stockedIds = findActiveStockedProductIds(matchedProducts.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .toList());

        for (Product product : matchedProducts) {
            if (product.getId() == null || !stockedIds.contains(product.getId())) {
                continue;
            }
            payload.getProposedItems().add(ProposedItemDto.builder()
                    .productId(product.getId())
                    .quantity(1)
                    .reason("Sản phẩm bạn đã yêu cầu trực tiếp.")
                    .build());
            payload.getExplanations().put(product.getId(), "Sản phẩm khớp yêu cầu trực tiếp.");
        }

        syncRecommendedIdsFromProposedItems(payload);
        if (payload.getProposedItems().isEmpty()) {
            payload.setTrustScore(65f);
            payload.setReply("Mình tìm thấy sản phẩm bạn yêu cầu nhưng hiện chưa có hàng. Bạn muốn mình gợi ý sản phẩm thay thế không?");
        } else {
            payload.setReply("Mình đã tạo danh sách mua sắm từ các sản phẩm bạn yêu cầu.");
        }
        return payload;
    }

    private List<Product> findExplicitlyRequestedProducts(String userMessage) {
        List<String> requestedPhrases = extractDirectProductPhrases(userMessage);
        if (requestedPhrases.isEmpty()) {
            return List.of();
        }

        List<Product> activeProducts;
        try {
            activeProducts = productRepository.findActiveWithCategory();
        } catch (Exception e) {
            log.warn("Could not load active products for direct product shopping: {}", e.getMessage());
            return List.of();
        }

        LinkedHashMap<Long, Product> matchedProducts = new LinkedHashMap<>();
        for (String phrase : requestedPhrases) {
            activeProducts.stream()
                    .filter(product -> product.getId() != null)
                    .filter(product -> directProductMatchesPhrase(product, phrase))
                    .max(Comparator.comparingInt(product -> directProductMatchScore(product, phrase)))
                    .ifPresent(product -> matchedProducts.putIfAbsent(product.getId(), product));
        }
        return matchedProducts.values().stream()
                .limit(12)
                .toList();
    }

    private List<String> extractDirectProductPhrases(String userMessage) {
        String n = normalizeText((userMessage == null ? "" : userMessage)
                .replace(",", " va ")
                .replace(";", " va ")
                .replace("/", " va "));
        if (n.isBlank()) {
            return List.of();
        }

        String requestText = n;
        for (String prefix : List.of(
                "tao danh sach mua sam cho",
                "tao danh sach mua hang cho",
                "tao danh sach mua do cho",
                "lap danh sach mua sam cho",
                "lap danh sach mua hang cho",
                "danh sach mua sam cho",
                "danh sach mua hang cho",
                "tao list mua do cho",
                "list mua do cho",
                "mua sam cho",
                "mua do cho",
                "mua cho",
                "them vao gio",
                "bo vao gio",
                "cho vao gio")) {
            if (requestText.contains(prefix)) {
                requestText = requestText.substring(requestText.indexOf(prefix) + prefix.length()).trim();
                break;
            }
        }

        for (String generic : List.of(
                "tao", "lap", "danh sach", "mua sam", "mua hang", "mua do",
                "shopping list", "list mua", "cho", "toi", "minh", "dum",
                "giup", "ho", "nhe", "nha", "can", "cac san pham", "san pham")) {
            requestText = requestText.replace(generic, " ");
        }
        requestText = requestText.replaceAll("\\s+", " ").trim();
        if (requestText.isBlank()) {
            return List.of();
        }

        return Arrays.stream(requestText.split("\\b(?:va|voi|cung|gom|them)\\b"))
                .map(String::trim)
                .map(phrase -> phrase.replaceAll("\\s+", " "))
                .filter(phrase -> phrase.length() >= 3)
                .distinct()
                .toList();
    }

    private boolean directProductMatchesPhrase(Product product, String phrase) {
        String normalizedPhrase = normalizeText(phrase);
        if (normalizedPhrase.isBlank()) {
            return false;
        }
        String haystack = productSearchText(product);
        if (haystack.contains(normalizedPhrase)) {
            return true;
        }
        List<String> phraseTokens = meaningfulProductTokens(normalizedPhrase);
        if (phraseTokens.size() >= 2 && phraseTokens.stream().allMatch(haystack::contains)) {
            return true;
        }

        String name = normalizeText(product.getName());
        List<String> nameTokens = meaningfulProductTokens(name);
        for (int i = 0; i + 1 < nameTokens.size(); i++) {
            String productPhrase = nameTokens.get(i) + " " + nameTokens.get(i + 1);
            if (normalizedPhrase.contains(productPhrase)) {
                return true;
            }
        }
        return false;
    }

    private int directProductMatchScore(Product product, String phrase) {
        String normalizedPhrase = normalizeText(phrase);
        String name = normalizeText(product.getName());
        String haystack = productSearchText(product);
        int score = 0;
        if (name.equals(normalizedPhrase)) {
            score += 300;
        }
        if (name.contains(normalizedPhrase)) {
            score += 180 + normalizedPhrase.length();
        }
        if (haystack.contains(normalizedPhrase)) {
            score += 120 + normalizedPhrase.length();
        }
        List<String> phraseTokens = meaningfulProductTokens(normalizedPhrase);
        for (String token : phraseTokens) {
            if (name.contains(token)) {
                score += 20;
            } else if (haystack.contains(token)) {
                score += 8;
            }
        }
        return score;
    }

    private List<String> meaningfulProductTokens(String text) {
        return Arrays.stream(normalizeText(text).split("\\s+"))
                .filter(token -> token.length() >= 2)
                .filter(token -> !Set.of(
                        "va", "voi", "cho", "mua", "san", "pham", "loai",
                        "hop", "chai", "goi", "kg", "ml", "lit").contains(token))
                .toList();
    }

    private ChatResponsePayload buildShoppingListFromDirectMealRequest(String userMessage) {
        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setIntentDetected("DIRECT_MEAL_SHOPPING_LIST");
        payload.setTrustScore(82f);
        payload.setRecommendedProductIds(new ArrayList<>());
        payload.setProposedItems(new ArrayList<>());
        payload.setRemoveVariantIds(new ArrayList<>());
        payload.setRemoveReasons(new HashMap<>());
        payload.setExplanations(new HashMap<>());

        List<String> ingredientTerms = extractDirectIngredientTerms(userMessage);
        if (ingredientTerms.isEmpty()) {
            payload.setIntentDetected("MEAL_SELECTION_CLARIFICATION");
            payload.setTrustScore(70f);
            payload.setReply("Bạn muốn mua nguyên liệu cho món nào? Ví dụ: ức gà áp chảo măng tây khoai lang.");
            return payload;
        }

        List<Product> activeProducts;
        try {
            activeProducts = productRepository.findActiveWithCategory();
        } catch (Exception e) {
            log.warn("Could not load active products for direct meal shopping list: {}", e.getMessage());
            payload.setReply("Mình đã ghi nhận món bạn nêu, nhưng dữ liệu sản phẩm hiện chưa sẵn sàng để tạo danh sách mua sắm.");
            return payload;
        }

        List<Long> activeProductIds = activeProducts.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .toList();
        Set<Long> stockedProductIds = findActiveStockedProductIds(activeProductIds);
        LinkedHashSet<Long> usedProductIds = new LinkedHashSet<>();
        MealOption directMeal = MealOption.builder()
                .optionNo(0)
                .title(extractDirectMealTitle(userMessage))
                .ingredients(ingredientTerms)
                .reason("nguyên liệu do người dùng nêu trực tiếp")
                .build();

        for (String ingredient : ingredientTerms) {
            findBestProductForIngredient(ingredient, directMeal, activeProducts, stockedProductIds, usedProductIds)
                    .ifPresent(product -> {
                        usedProductIds.add(product.getId());
                        payload.getProposedItems().add(ProposedItemDto.builder()
                                .productId(product.getId())
                                .quantity(1)
                                .reason("Nguyên liệu cho món bạn đã nêu.")
                                .build());
                        payload.getExplanations().put(product.getId(), "Khớp nguyên liệu: " + ingredient);
                    });
        }

        if (!payload.getProposedItems().isEmpty()) {
            filterOutOfStock(payload.getProposedItems());
            filterNonFoodForMealIntent(payload, userMessage);
            filterExcludedIngredients(payload, userMessage);
            filterLowQualityMealItems(payload, userMessage);
            syncRecommendedIdsFromProposedItems(payload);
        }

        if (payload.getProposedItems().isEmpty()) {
            payload.setTrustScore(60f);
            payload.setReply("Mình chưa tìm được sản phẩm còn hàng khớp với nguyên liệu bạn nêu. Bạn có muốn mình gợi ý thực đơn khác không?");
            return payload;
        }

        Set<String> matchedTerms = new LinkedHashSet<>();
        if (payload.getExplanations() != null) {
            payload.getExplanations().values().stream()
                    .map(reason -> reason.replace("Khớp nguyên liệu:", "").trim())
                    .filter(reason -> !reason.isBlank())
                    .forEach(matchedTerms::add);
        }
        List<String> missingTerms = ingredientTerms.stream()
                .filter(term -> !matchedTerms.contains(term))
                .toList();

        if (missingTerms.isEmpty()) {
            payload.setReply("Mình đã tạo danh sách mua sắm từ các nguyên liệu bạn nêu.");
        } else {
            payload.setReply("Mình đã tạo danh sách mua sắm từ các nguyên liệu tìm được. Hiện chưa khớp được hoặc chưa còn hàng: "
                    + String.join(", ", missingTerms) + ".");
            payload.setTrustScore(72f);
        }
        return payload;
    }

    private List<String> extractDirectIngredientTerms(String userMessage) {
        String n = normalizeText(userMessage);
        List<String> knownTerms = List.of(
                "uc ga", "thit ga", "ga", "trung", "dau hu", "dau phu",
                "mang tay", "khoai lang", "xa lach", "dua leo", "tao",
                "yen mach", "nam", "su hao", "gao lut", "bun gao lut",
                "sua tuoi tach beo", "sua hanh nhan", "blueberry",
                "rau xanh", "bap cai", "bong cai", "ca rot", "bi do",
                "thit bo", "bo", "thit heo nac", "heo nac"
        );
        return knownTerms.stream()
                .filter(term -> containsIngredientTerm(n, term))
                .filter(term -> !"ga".equals(term) || !n.contains("uc ga") && !n.contains("thit ga"))
                .filter(term -> !"bo".equals(term) || !n.contains("thit bo"))
                .distinct()
                .toList();
    }

    private boolean containsIngredientTerm(String normalizedMessage, String term) {
        if ("tao".equals(term)) {
            return normalizedMessage.equals("tao")
                    || containsNormalizedPhrase(normalizedMessage, "qua tao")
                    || containsNormalizedPhrase(normalizedMessage, "trai tao")
                    || containsNormalizedPhrase(normalizedMessage, "salad tao")
                    || normalizedMessage.endsWith(" tao");
        }
        return containsNormalizedPhrase(normalizedMessage, term);
    }

    private String extractDirectMealTitle(String userMessage) {
        String normalized = normalizeText(userMessage);
        String title = normalized;
        for (String prefix : List.of(
                "tao danh sach mua sam cho mon",
                "tao danh sach mua sam cho",
                "tao list mua do cho mon",
                "tao list mua do cho",
                "mua nguyen lieu cho mon",
                "mua nguyen lieu cho",
                "danh sach mua sam cho mon",
                "danh sach mua sam cho")) {
            if (title.contains(prefix)) {
                title = title.substring(title.indexOf(prefix) + prefix.length()).trim();
                break;
            }
        }
        return title.isBlank() ? "món bạn đã nêu" : title;
    }

    private ChatResponsePayload buildShoppingListFromSelectedMealPayload(String userMessage, String sessionContext) {
        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setIntentDetected("SHOPPING_LIST_CREATE");
        payload.setTrustScore(82f);
        payload.setRecommendedProductIds(new ArrayList<>());
        payload.setProposedItems(new ArrayList<>());
        payload.setRemoveVariantIds(new ArrayList<>());
        payload.setRemoveReasons(new HashMap<>());
        payload.setExplanations(new HashMap<>());

        List<MealOption> options = readMealOptions(sessionContext);
        OptionalInt selectedNo = parseSelectedMealOptionNo(userMessage);
        if (options.isEmpty() || selectedNo.isEmpty()) {
            payload.setIntentDetected("MEAL_SELECTION_CLARIFICATION");
            payload.setReply("Bạn muốn chọn thực đơn số mấy? Nếu chưa có thực đơn, hãy nói mục tiêu như: bữa tối giảm cân hoặc bữa sáng healthy.");
            return payload;
        }

        MealOption selected = options.stream()
                .filter(option -> option.getOptionNo() == selectedNo.getAsInt())
                .findFirst()
                .orElse(null);
        if (selected == null) {
            payload.setIntentDetected("MEAL_SELECTION_CLARIFICATION");
            payload.setReply("Mình chưa thấy thực đơn số " + selectedNo.getAsInt() + ". Bạn chọn lại một số trong các thực đơn vừa gợi ý nhé.");
            return payload;
        }

        List<Product> activeProducts;
        try {
            activeProducts = productRepository.findActiveWithCategory();
        } catch (Exception e) {
            log.warn("Could not load active products for selected meal: {}", e.getMessage());
            payload.setReply("Mình đã ghi nhận thực đơn bạn chọn, nhưng dữ liệu sản phẩm hiện chưa sẵn sàng để tạo danh sách mua sắm.");
            return payload;
        }

        List<Long> activeProductIds = activeProducts.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .toList();
        Set<Long> stockedProductIds = findActiveStockedProductIds(activeProductIds);
        LinkedHashSet<Long> usedProductIds = new LinkedHashSet<>();

        for (String ingredient : selected.getIngredients()) {
            findBestProductForIngredient(ingredient, selected, activeProducts, stockedProductIds, usedProductIds)
                    .ifPresent(product -> {
                        usedProductIds.add(product.getId());
                        payload.getProposedItems().add(ProposedItemDto.builder()
                                .productId(product.getId())
                                .quantity(1)
                                .reason("Nguyên liệu cho " + selected.getTitle() + ".")
                                .build());
                        payload.getExplanations().put(product.getId(), "Khớp nguyên liệu: " + ingredient);
                    });
        }

        syncRecommendedIdsFromProposedItems(payload);
        if (payload.getProposedItems().isEmpty()) {
            payload.setReply("Mình đã ghi nhận thực đơn " + selected.getTitle()
                    + ", nhưng hiện chưa tìm được nguyên liệu còn hàng phù hợp để tạo danh sách mua sắm.");
            payload.setTrustScore(60f);
            return payload;
        }

        payload.setReply("Mình đã tạo danh sách mua sắm cho thực đơn " + selected.getTitle()
                + ". Mình chỉ đưa vào các nguyên liệu chính còn hàng; gia vị phụ như muối, tiêu hoặc dầu ăn chỉ nên thêm nếu bạn thật sự cần mua.");
        return payload;
    }

    private Optional<Product> findBestProductForIngredient(
            String ingredient,
            MealOption selected,
            List<Product> products,
            Set<Long> stockedProductIds,
            Set<Long> usedProductIds
    ) {
        String scoringContext = selected.getTitle() + " healthy protein giam can";
        List<String> aliases = ingredientAliases(ingredient);
        return products.stream()
                .filter(product -> product.getId() != null && stockedProductIds.contains(product.getId()))
                .filter(product -> !usedProductIds.contains(product.getId()))
                .filter(product -> ingredientMatchesProduct(product, aliases))
                .filter(product -> ingredientCompatibleWithProduct(ingredient, product))
                .filter(product -> isMealCandidateAllowed(
                        scoringContext,
                        product.getName(),
                        safeCategoryName(product),
                        product.getDescription()
                ))
                .max(Comparator.comparingInt(product -> ingredientProductScore(product, aliases, scoringContext)));
    }

    private boolean ingredientMatchesProduct(Product product, List<String> aliases) {
        String haystack = productSearchText(product);
        return aliases.stream().anyMatch(alias -> containsNormalizedPhrase(haystack, alias));
    }

    private int ingredientProductScore(Product product, List<String> aliases, String scoringContext) {
        String name = normalizeText(product.getName());
        String haystack = productSearchText(product);
        int score = mealCandidateScore(scoringContext, product.getName(), safeCategoryName(product), product.getDescription());
        for (String alias : aliases) {
            if (containsNormalizedPhrase(name, alias)) {
                score += 100;
            } else if (containsNormalizedPhrase(haystack, alias)) {
                score += 40;
            }
        }
        if (Boolean.TRUE.equals(product.getIsStaple())) {
            score -= 30;
        }
        return score;
    }

    private boolean ingredientCompatibleWithProduct(String ingredient, Product product) {
        String normalizedIngredient = normalizeText(ingredient);
        String productText = productSearchText(product);
        if (requiresChicken(normalizedIngredient)) {
            return isChickenProduct(productText);
        }
        if (requiresBeef(normalizedIngredient)) {
            return isBeefProduct(productText);
        }
        if (requiresPork(normalizedIngredient)) {
            return isPorkProduct(productText);
        }
        return true;
    }

    private boolean containsNormalizedPhrase(String normalizedText, String phrase) {
        String text = normalizeText(normalizedText);
        String normalizedPhrase = normalizeText(phrase);
        if (text.isBlank() || normalizedPhrase.isBlank()) {
            return false;
        }
        return (" " + text + " ").contains(" " + normalizedPhrase + " ");
    }

    private String productSearchText(Product product) {
        if (product == null) {
            return "";
        }
        return normalizeText(String.join(" ",
                product.getName() != null ? product.getName() : "",
                safeCategoryName(product),
                product.getShortDescription() != null ? product.getShortDescription() : "",
                product.getDescription() != null ? product.getDescription() : ""
        ));
    }

    private List<String> ingredientAliases(String ingredient) {
        String n = normalizeText(ingredient);
        if (n.contains("uc ga")) return List.of("uc ga", "thit ga", "ga");
        if (n.contains("mang tay")) return List.of("mang tay");
        if (n.contains("khoai lang")) return List.of("khoai lang");
        if (n.contains("dau hu") || n.contains("dau phu")) return List.of("dau hu", "dau phu");
        if (n.contains("nam")) return List.of("nam");
        if (n.contains("su hao")) return List.of("su hao");
        if (n.contains("yen mach")) return List.of("yen mach", "oat");
        if (n.contains("trung")) return List.of("trung", "egg");
        if (n.contains("xa lach")) return List.of("xa lach", "lettuce", "rau xanh", "rau");
        if (n.contains("dua leo")) return List.of("dua leo", "dưa leo", "cucumber");
        if (n.equals("tao") || n.contains(" tao")) return List.of("tao", "apple");
        if (n.contains("sua tuoi tach beo")) return List.of("sua tuoi tach beo", "sua tach beo");
        if (n.contains("sua hanh nhan")) return List.of("sua hanh nhan", "hanh nhan");
        if (n.contains("blueberry")) return List.of("blueberry", "viet quat");
        if (n.contains("rau xanh")) return List.of("rau xanh", "rau", "xa lach", "bap cai", "mong toi");
        return List.of(n);
    }

    private boolean shouldAskClarificationForBareShoppingList(String userMessage, String sessionContext) {
        if (!isShoppingListRequest(userMessage) || isMealOrDietIntent(userMessage)) {
            return false;
        }
        if (!readShoppingCandidateIds(sessionContext).isEmpty()
                && readLastShoppingCandidateMealIntent(sessionContext)) {
            return false;
        }
        String n = normalizeText(userMessage);
        boolean exactBareRequest = n.equals("tao danh sach mua sam dum toi")
                || n.equals("tao danh sach mua sam giup toi")
                || n.equals("tao danh sach mua sam")
                || n.equals("tao danh sach mua hang")
                || n.equals("tao danh sach mua do")
                || n.equals("lap danh sach mua sam")
                || n.equals("danh sach mua sam")
                || n.equals("shopping list")
                || n.equals("tao shopping list");
        if (exactBareRequest) {
            return true;
        }

        boolean hasSpecificShoppingGoal = n.contains("bua sang")
                || n.contains("bua trua")
                || n.contains("bua toi")
                || n.contains("an sang")
                || n.contains("an trua")
                || n.contains("an toi")
                || n.contains("giam can")
                || n.contains("tang can")
                || n.contains("tang co")
                || n.contains("protein")
                || n.contains("healthy")
                || n.contains("an chay")
                || n.contains("khong an")
                || n.contains("di ung")
                || n.contains("cho be")
                || n.contains("cho tre")
                || n.contains("nguoi")
                || n.contains("ngay")
                || n.contains("mon")
                || n.contains("nau");
        if (hasSpecificShoppingGoal) {
            return false;
        }

        String remaining = n;
        for (String generic : List.of(
                "tao", "lap", "cho", "toi", "minh", "dum", "giup", "ho",
                "danh sach", "mua sam", "mua hang", "mua do", "shopping list",
                "di", "nhe", "nha", "can")) {
            remaining = remaining.replace(generic, " ");
        }
        remaining = remaining.replaceAll("\\s+", " ").trim();
        return remaining.isBlank();
    }

    private boolean isModificationRequest(String userMessage) {
        String n = normalizeText(userMessage);
        return n.contains(" doi ")
                || n.startsWith("doi ")
                || n.contains(" thay ")
                || n.startsWith("thay ")
                || n.contains(" thay the ")
                || n.contains(" bo ")
                || n.startsWith("bo ")
                || n.contains(" khong thich ")
                || n.contains(" khong lay ")
                || n.contains(" dung lay ")
                || n.contains(" loai ")
                || n.startsWith("loai ")
                || n.contains(" them ");
    }

    private void updateShoppingSessionContext(
            Long sessionId,
            ChatResponsePayload payload,
            List<ProductNode> discoveredProducts,
            String userMessage
    ) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (payload.getProposedItems() != null && !payload.getProposedItems().isEmpty()) {
            payload.getProposedItems().stream()
                    .map(ProposedItemDto::getProductId)
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .forEach(ids::add);
        } else if (!isShoppingListRequest(userMessage) && discoveredProducts != null) {
            discoveredProducts.stream()
                    .map(ProductNode::getProductId)
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .limit(12)
                    .forEach(ids::add);
        }
        if (ids.isEmpty()) {
            return;
        }

        Set<Long> stockedIds = findActiveStockedProductIds(ids);
        List<Long> safeIds = ids.stream()
                .filter(stockedIds::contains)
                .limit(12)
                .toList();
        if (safeIds.isEmpty()) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            ChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Chat session not found"));
            Map<String, Object> context = readSessionContextMap(session.getSessionContext());
            context.put("lastShoppingCandidateIds", safeIds);
            context.put("lastShoppingCandidateMealIntent",
                    isMealOrDietIntent(userMessage)
                            || "SHOPPING_LIST_CREATE".equals(payload.getIntentDetected())
                            || "DIRECT_MEAL_SHOPPING_LIST".equals(payload.getIntentDetected()));
            context.put("lastShoppingCandidateUpdatedAt", LocalDateTime.now().toString());
            try {
                session.setSessionContext(objectMapper.writeValueAsString(context));
                sessionRepository.save(session);
            } catch (Exception e) {
                log.warn("Could not update shopping session context: {}", e.getMessage());
            }
        });
    }

    private void updateMealOptionsSessionContext(Long sessionId, List<MealOption> options, String userMessage) {
        if (options == null || options.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            ChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Chat session not found"));
            Map<String, Object> context = readSessionContextMap(session.getSessionContext());
            context.put("lastMealOptions", options.stream()
                    .map(this::mealOptionToMap)
                    .toList());
            context.put("lastMealOptionsGoal", normalizeText(userMessage));
            context.put("lastMealOptionsUpdatedAt", LocalDateTime.now().toString());
            context.remove("lastShoppingCandidateIds");
            context.remove("lastShoppingCandidateMealIntent");
            try {
                session.setSessionContext(objectMapper.writeValueAsString(context));
                sessionRepository.save(session);
            } catch (Exception e) {
                log.warn("Could not update meal options session context: {}", e.getMessage());
            }
        });
    }

    private Map<String, Object> mealOptionToMap(MealOption option) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("optionNo", option.getOptionNo());
        map.put("title", option.getTitle());
        map.put("ingredients", option.getIngredients());
        map.put("reason", option.getReason());
        return map;
    }

    private List<Long> readShoppingCandidateIds(String sessionContext) {
        Object value = readSessionContextMap(sessionContext).get("lastShoppingCandidateIds");
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::coercePositiveLong)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    private List<MealOption> readMealOptions(String sessionContext) {
        Object value = readSessionContextMap(sessionContext).get("lastMealOptions");
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<MealOption> options = new ArrayList<>();
        for (Object rawOption : values) {
            if (!(rawOption instanceof Map<?, ?> map)) {
                continue;
            }
            Optional<Long> optionNo = coercePositiveLong(map.get("optionNo"));
            if (optionNo.isEmpty()) {
                continue;
            }
            List<String> ingredients = new ArrayList<>();
            Object rawIngredients = map.get("ingredients");
            if (rawIngredients instanceof List<?> rawIngredientList) {
                rawIngredientList.stream()
                        .map(String::valueOf)
                        .filter(s -> !s.isBlank())
                        .forEach(ingredients::add);
            }
            Object rawTitle = map.get("title");
            Object rawReason = map.get("reason");
            options.add(MealOption.builder()
                    .optionNo(optionNo.get().intValue())
                    .title(rawTitle != null ? String.valueOf(rawTitle) : "Thực đơn số " + optionNo.get())
                    .ingredients(ingredients)
                    .reason(rawReason != null ? String.valueOf(rawReason) : "phù hợp với mục tiêu đã chọn")
                    .build());
        }
        return options;
    }

    private boolean readLastShoppingCandidateMealIntent(String sessionContext) {
        Object value = readSessionContextMap(sessionContext).get("lastShoppingCandidateMealIntent");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private Map<String, Object> readSessionContextMap(String sessionContext) {
        if (sessionContext == null || sessionContext.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<?, ?> raw = objectMapper.readValue(sessionContext, Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        } catch (Exception e) {
            log.warn("Could not parse session context JSON, recreating context: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Optional<Long> coercePositiveLong(Object value) {
        if (value instanceof Number number) {
            long id = number.longValue();
            return id > 0 ? Optional.of(id) : Optional.empty();
        }
        return parsePositiveLong(value == null ? null : String.valueOf(value));
    }

    private void filterPantryStaples(List<ProposedItemDto> proposedItems, String userMessage) {
        // Check for explicit intent keywords in Vietnamese
        boolean explicitStapleRequest = normalizeText(userMessage).contains("het") || 
                                      normalizeText(userMessage).contains("mua them") || 
                                      normalizeText(userMessage).contains("thieu") || 
                                      normalizeText(userMessage).contains("can mua");
        
        if (explicitStapleRequest) {
            return; // User explicitly asked for staples, bypass filter
        }

        List<Long> productIds = proposedItems.stream()
                .map(ProposedItemDto::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        proposedItems.removeIf(item -> {
            Product product = productsById.get(item.getProductId());
            if (product != null && Boolean.TRUE.equals(product.getIsStaple())) {
                log.info("Backend Guard: Filtering out pantry staple '{}' (ID: {}) as it was not explicitly requested",
                        product.getName(), item.getProductId());
                return true;
            }
            return false;
        });
    }

    private void enforceRecipeIngredientConsistency(ChatResponsePayload payload, String userMessage) {
        String context = String.join(" ",
                userMessage == null ? "" : userMessage,
                payload.getReply() == null ? "" : payload.getReply(),
                payload.getThoughtProcess() == null ? "" : payload.getThoughtProcess());

        boolean userWantsBeef = requiresBeef(userMessage);
        boolean userWantsPork = !userWantsBeef && requiresPork(userMessage);
        boolean userWantsChicken = !userWantsBeef && !userWantsPork && requiresChicken(userMessage);
        boolean beefIntent = userWantsBeef || (requiresBeef(context) && !userWantsPork && !userWantsChicken);
        boolean porkIntent = userWantsPork || (!beefIntent && requiresPork(context) && !userWantsChicken);
        boolean chickenIntent = userWantsChicken || (!beefIntent && !porkIntent && requiresChicken(context));

        if (!beefIntent && !porkIntent && !chickenIntent) {
            return;
        }

        List<Long> productIds = new ArrayList<>();
        if (payload.getProposedItems() != null) {
            payload.getProposedItems().stream()
                    .map(ProposedItemDto::getProductId)
                    .filter(Objects::nonNull)
                    .forEach(productIds::add);
        }
        if (payload.getRecommendedProductIds() != null) {
            payload.getRecommendedProductIds().stream()
                    .filter(Objects::nonNull)
                    .forEach(productIds::add);
        }
        List<Long> distinctIds = productIds.stream().distinct().toList();

        Map<Long, Product> productsById = productRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        int before = payload.getProposedItems().size();
        payload.getProposedItems().removeIf(item -> {
            Product product = productsById.get(item.getProductId());
            if (product == null || !"ACTIVE".equalsIgnoreCase(product.getStatus())) {
                log.info("AI Guard: removing inactive/missing product ID {}", item.getProductId());
                return true;
            }

            String productName = product.getName();
            boolean wrongForBeefDish = beefIntent && (isPorkProduct(productName) || isChickenProduct(productName));
            boolean wrongForPorkDish = porkIntent && (isBeefProduct(productName) || isChickenProduct(productName));
            boolean wrongForChickenDish = chickenIntent && (isBeefProduct(productName) || isPorkProduct(productName));

            if (wrongForBeefDish || wrongForPorkDish || wrongForChickenDish) {
                log.info("AI Guard: removing incompatible ingredient '{}' (ID: {}) for requested recipe",
                        productName, item.getProductId());
                return true;
            }
            return false;
        });

        if (payload.getRecommendedProductIds() != null && !payload.getRecommendedProductIds().isEmpty()) {
            Set<Long> keptIds = payload.getProposedItems().stream()
                    .map(ProposedItemDto::getProductId)
                    .collect(Collectors.toSet());
            payload.getRecommendedProductIds().removeIf(id -> !keptIds.contains(id) && isIncompatibleProductId(id, context, productsById));
        }

        if (payload.getProposedItems().size() < before) {
            appendCorrection(payload, "Minh da loai cac nguyen lieu khong khop mon an de tranh goi y sai. Beefsteak phai dung thit bo; khong dung nac dam heo de lam beefsteak.");
            if (payload.getTrustScore() != null) {
                payload.setTrustScore(Math.min(payload.getTrustScore(), 70f));
            }
        }
    }

    private void enforceReplyConsistency(ChatResponsePayload payload, String userMessage) {
        String context = String.join(" ",
                userMessage == null ? "" : userMessage,
                payload.getReply() == null ? "" : payload.getReply());

        if (requiresBeef(context) && containsPorkTerms(payload.getReply())) {
            payload.setReply("Minh xin chinh lai: beefsteak phai dung thit bo, khong dung nac dam heo. "
                    + "Neu kho hien khong co thit bo phu hop, minh se goi y mon khac tu heo thay vi goi do la beefsteak.");
            payload.setProposedItems(new ArrayList<>());
            payload.setRecommendedProductIds(new ArrayList<>());
            payload.setTrustScore(payload.getTrustScore() == null ? 60f : Math.min(payload.getTrustScore(), 60f));
            log.info("AI Guard: replaced incompatible beefsteak/pork reply");
        }
    }

    private boolean isIncompatibleProductId(Long productId, String context, Map<Long, Product> knownProducts) {
        Product product = knownProducts.get(productId);
        if (product == null) {
            return true;
        }

        String name = product.getName();
        if (requiresBeef(context)) {
            return isPorkProduct(name) || isChickenProduct(name);
        }
        if (requiresPork(context)) {
            return isBeefProduct(name) || isChickenProduct(name);
        }
        if (requiresChicken(context)) {
            return isBeefProduct(name) || isPorkProduct(name);
        }
        return false;
    }

    private void appendCorrection(ChatResponsePayload payload, String correction) {
        String reply = payload.getReply();
        if (reply == null || reply.isBlank()) {
            payload.setReply(correction);
        } else if (!normalizeText(reply).contains(normalizeText(correction))) {
            payload.setReply(reply + "\n\n" + correction);
        }
    }

    /**
     * Backend guard: removes any proposed items that are out of stock.
     * Even if the AI somehow suggests an unavailable product, this ensures
     * the frontend never shows a product card with stock = 0.
     */
    private void filterOutOfStock(List<ProposedItemDto> proposedItems) {
        List<Long> productIds = proposedItems.stream()
                .map(ProposedItemDto::getProductId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        Set<Long> stockedProductIds = findActiveStockedProductIds(productIds);

        Set<Long> removedProductIds = new LinkedHashSet<>();
        proposedItems.removeIf(item -> {
            Long productId = item.getProductId();
            boolean remove = productId == null || productId <= 0 || !stockedProductIds.contains(productId);
            if (remove) {
                removedProductIds.add(productId);
            }
            return remove;
        });
        if (!removedProductIds.isEmpty()) {
            log.info("Backend Guard: Removed product IDs {} - product missing, inactive, invalid, or out of stock", removedProductIds);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONTEXT QUALITY FILTERS (Non-Food / Exclusion / Intent)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Removes non-food products (cleaning, personal care, household, alcohol, etc.)
     * when the user's intent is meal-related or diet-related.
     */
    private void filterNonFoodForMealIntent(ChatResponsePayload payload, String userMessage) {
        if (!isMealOrDietIntent(userMessage)) {
            return;
        }
        List<Long> productIds = payload.getProposedItems().stream()
                .map(ProposedItemDto::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) return;

        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        int before = payload.getProposedItems().size();
        payload.getProposedItems().removeIf(item -> {
            Product product = productsById.get(item.getProductId());
            if (product == null) return true;

            String normalizedName = normalizeText(product.getName());
            // Check product name against non-food denylist
            for (String deny : NON_FOOD_NAME_DENY) {
                if (normalizedName.contains(deny)) {
                    log.info("Context Guard: rejected '{}' (ID:{}) for meal intent — matched non-food pattern '{}'",
                            product.getName(), item.getProductId(), deny);
                    return true;
                }
            }

            // Check category name if available
            try {
                String catName = normalizeText(product.getCategory() != null ? product.getCategory().getName() : "");
                if (catName.contains("ve sinh") || catName.contains("lam sach") || catName.contains("cham soc")
                        || catName.contains("dung cu") || catName.contains("gia dung") || catName.contains("household")
                        || catName.contains("personal care") || catName.contains("cleaning")) {
                    log.info("Context Guard: rejected '{}' (ID:{}) — non-food category '{}'",
                            product.getName(), item.getProductId(), product.getCategory().getName());
                    return true;
                }
            } catch (Exception e) {
                // Category lazy load may fail, skip category check
            }
            return false;
        });

        if (payload.getProposedItems().size() < before) {
            log.info("Context Guard: removed {} non-food items for meal/diet intent",
                    before - payload.getProposedItems().size());
        }
    }

    /**
     * Parses negative constraints from user message and hard-blocks matching products.
     * E.g., "không ăn hải sản" → remove all seafood.
     */
    private void filterExcludedIngredients(ChatResponsePayload payload, String userMessage) {
        if (!excludesSeafood(userMessage)) return;

        List<Long> productIds = payload.getProposedItems().stream()
                .map(ProposedItemDto::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) return;

        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        int before = payload.getProposedItems().size();
        payload.getProposedItems().removeIf(item -> {
            Product product = productsById.get(item.getProductId());
            if (product == null) return true;

            String normalizedName = normalizeText(product.getName());
            for (String deny : SEAFOOD_NAME_DENY) {
                if (normalizedName.contains(deny)) {
                    log.info("Exclusion Guard: rejected '{}' (ID:{}) — user excluded seafood, matched '{}'",
                            product.getName(), item.getProductId(), deny);
                    return true;
                }
            }
            return false;
        });

        if (payload.getProposedItems().size() < before) {
            log.info("Exclusion Guard: removed {} seafood items per user constraint",
                    before - payload.getProposedItems().size());
            // Also clean the reply to not mention removed items
            if (payload.getReply() != null) {
                String reply = payload.getReply();
                if (!reply.contains("Không có") && !reply.contains("Không có")) {
                    payload.setReply(reply + "\n\nMình đã loại bỏ tất cả sản phẩm hải sản theo yêu cầu của bạn.");
                }
            }
        }
    }

    private boolean excludesSeafood(String userMessage) {
        String normalizedMsg = normalizeText(userMessage);
        return normalizedMsg.contains("khong an hai san")
                || normalizedMsg.contains("khong thich hai san")
                || normalizedMsg.contains("di ung hai san")
                || normalizedMsg.contains("khong hai san")
                || normalizedMsg.contains("bo hai san")
                || normalizedMsg.contains("tranh hai san")
                || normalizedMsg.contains("no seafood")
                || normalizedMsg.contains("khong an tom")
                || normalizedMsg.contains("khong an ca");
    }

    private void filterLowQualityMealItems(ChatResponsePayload payload, String userMessage) {
        if (!isMealOrDietIntent(userMessage) || payload.getProposedItems() == null || payload.getProposedItems().isEmpty()) {
            return;
        }
        List<Long> productIds = payload.getProposedItems().stream()
                .map(ProposedItemDto::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        int before = payload.getProposedItems().size();
        payload.getProposedItems().removeIf(item -> {
            Product product = productsById.get(item.getProductId());
            if (product == null) {
                return true;
            }
            return !isMealCandidateAllowed(
                    userMessage,
                    product.getName(),
                    product.getCategory() != null ? product.getCategory().getName() : "",
                    product.getDescription()
            );
        });

        if (payload.getProposedItems().size() < before) {
            log.info("Meal Quality Guard: removed {} low-fit products for meal/diet intent",
                    before - payload.getProposedItems().size());
        }
    }

    private void refillProposedItemsIfTooFew(ChatResponsePayload payload, String userMessage) {
        if (!isMealOrDietIntent(userMessage) || !isShoppingListRequest(userMessage)) {
            return;
        }
        List<ProposedItemDto> items = payload.getProposedItems();
        if (items != null && items.size() < 4) {
            int originalSize = items.size();
            log.info("Context Guard: proposed items count is {} after filters. Refilling to ensure quality...", originalSize);
            Set<Long> existingIds = items.stream()
                    .map(ProposedItemDto::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<Product> fallbacks = deterministicMealFallbackProducts(userMessage);
            List<Long> fallbackIds = fallbacks.stream().map(Product::getId).toList();
            Set<Long> stockedIds = findActiveStockedProductIds(fallbackIds);

            for (Product fallback : fallbacks) {
                if (items.size() >= 5) {
                    break;
                }
                if (!existingIds.contains(fallback.getId()) && stockedIds.contains(fallback.getId())) {
                    items.add(ProposedItemDto.builder()
                            .productId(fallback.getId())
                            .quantity(1)
                            .reason("Bổ sung nguyên liệu phù hợp cho bữa ăn.")
                            .build());
                    existingIds.add(fallback.getId());
                }
            }
            if (items.size() > originalSize) {
                log.info("Context Guard: refilled proposed items from {} to {}", originalSize, items.size());
            }
        }
    }

    private void syncRecommendedIdsFromProposedItems(ChatResponsePayload payload) {
        if (payload.getProposedItems() == null || payload.getProposedItems().isEmpty()) {
            return;
        }
        payload.setRecommendedProductIds(new ArrayList<>(payload.getProposedItems().stream()
                .map(ProposedItemDto::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList()));
    }

    private boolean isMealSuggestionOnly(String userMessage) {
        String n = normalizeText(userMessage);
        return !isShoppingListRequest(userMessage)
                && (n.contains("goi y") || n.contains("suggest"))
                && isMealOrDietIntent(userMessage);
    }

    private void keepSuggestionAsCandidatesOnly(ChatResponsePayload payload) {
        if (payload.getProposedItems() == null || payload.getProposedItems().isEmpty()) {
            return;
        }
        payload.setRecommendedProductIds(new ArrayList<>(payload.getProposedItems().stream()
                .map(ProposedItemDto::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList()));
        payload.setProposedItems(new ArrayList<>());
    }

    /**
     * Checks if the user's message indicates a meal, diet, or food shopping intent.
     */
    private boolean isMealOrDietIntent(String userMessage) {
        String n = normalizeText(userMessage);
        return n.contains("bua toi") || n.contains("bua sang") || n.contains("bua trua")
                || n.contains("an toi") || n.contains("an sang") || n.contains("an trua")
                || n.contains("giam can") || n.contains("tang can") || n.contains("healthy")
                || n.contains("diet") || n.contains("protein") || n.contains("dinh duong")
                || n.contains("thuc don") || n.contains("meal") || n.contains("nau")
                || n.contains("mon an") || n.contains("cong thuc") || n.contains("recipe")
                || n.contains("no lau") || n.contains("nhe nhang") || n.contains("khong ngay")
                || n.contains("beefsteak") || n.contains("steak")
                || n.contains("an kieng") || n.contains("it calo") || n.contains("low calorie");
    }

    private void filterRecommendedProductIds(ChatResponsePayload payload, String userMessage) {
        if (payload.getRecommendedProductIds() == null || payload.getRecommendedProductIds().isEmpty()) {
            return;
        }
        payload.setRecommendedProductIds(new ArrayList<>(payload.getRecommendedProductIds()));
        payload.getRecommendedProductIds().removeIf(productId -> productId == null || productId <= 0);
        if (payload.getRecommendedProductIds().isEmpty()) {
            return;
        }
        Set<Long> stockedProductIds = findActiveStockedProductIds(payload.getRecommendedProductIds());
        payload.getRecommendedProductIds().removeIf(productId -> !stockedProductIds.contains(productId));
        if (isMealOrDietIntent(userMessage) && !payload.getRecommendedProductIds().isEmpty()) {
            Map<Long, Product> productsById = productRepository.findAllByIdWithCategory(payload.getRecommendedProductIds()).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));
            payload.getRecommendedProductIds().removeIf(productId -> {
                Product product = productsById.get(productId);
                return product == null || !isMealCandidateAllowed(
                        userMessage,
                        product.getName(),
                        safeCategoryName(product),
                        product.getDescription()
                );
            });
        }
    }

    private String safeCategoryName(Product product) {
        if (product == null || product.getCategory() == null) {
            return "";
        }
        try {
            return product.getCategory().getName();
        } catch (RuntimeException e) {
            log.debug("Could not read product category name for product {}: {}",
                    product.getId(), e.getMessage());
            return "";
        }
    }

    private void ensureMutableCollections(ChatResponsePayload payload) {
        if (payload == null) {
            return;
        }
        payload.setRecommendedProductIds(payload.getRecommendedProductIds() == null
                ? new ArrayList<>()
                : new ArrayList<>(payload.getRecommendedProductIds()));
        payload.setProposedItems(payload.getProposedItems() == null
                ? new ArrayList<>()
                : new ArrayList<>(payload.getProposedItems()));
        payload.setRemoveVariantIds(payload.getRemoveVariantIds() == null
                ? new ArrayList<>()
                : new ArrayList<>(payload.getRemoveVariantIds()));
        payload.setRemoveReasons(payload.getRemoveReasons() == null
                ? new HashMap<>()
                : new HashMap<>(payload.getRemoveReasons()));
        payload.setExplanations(payload.getExplanations() == null
                ? new HashMap<>()
                : new HashMap<>(payload.getExplanations()));
    }

    private Set<Long> findActiveStockedProductIds(Collection<Long> productIds) {
        List<Long> ids = productIds == null ? List.of() : productIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Set.of();
        }

        try {
            Set<Long> activeProductIds = productRepository.findAllById(ids).stream()
                    .filter(product -> "ACTIVE".equalsIgnoreCase(product.getStatus()))
                    .map(Product::getId)
                    .collect(Collectors.toSet());
            if (activeProductIds.isEmpty()) {
                return Set.of();
            }

            List<ProductVariant> variants = productVariantRepository.findByProductIdsAndStatusWithProduct(
                            new ArrayList<>(activeProductIds),
                            "ACTIVE"
                    ).stream()
                    .filter(this::isActiveVariantForActiveProduct)
                    .toList();
            if (variants.isEmpty()) {
                return Set.of();
            }

            List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();
            Map<Long, Long> stockByVariantId = inventoryStockRepository.sumAvailableByVariantIds(variantIds).stream()
                    .collect(Collectors.toMap(
                            InventoryStockRepository.VariantStockSum::getVariantId,
                            InventoryStockRepository.VariantStockSum::getTotalAvailable
                    ));

            Map<Long, Long> stockByProductId = new HashMap<>();
            for (ProductVariant variant : variants) {
                long stock = stockByVariantId.getOrDefault(variant.getId(), 0L);
                Long productId = variant.getProduct().getId();
                stockByProductId.put(productId, stockByProductId.getOrDefault(productId, 0L) + stock);
            }

            return stockByProductId.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Batch stock guard failed: {}", e.getMessage());
            return Set.of();
        }
    }

    private Optional<Long> parsePositiveLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        long value;
        if (node.isIntegralNumber()) {
            value = node.asLong();
        } else if (node.isTextual()) {
            try {
                value = Long.parseLong(node.asText().trim());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
        return value > 0 ? Optional.of(value) : Optional.empty();
    }

    private Optional<Long> parsePositiveLong(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            long value = Long.parseLong(text.trim());
            return value > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private boolean isActiveVariantForActiveProduct(ProductVariant variant) {
        return variant != null
                && "ACTIVE".equalsIgnoreCase(variant.getStatus())
                && variant.getProduct() != null
                && "ACTIVE".equalsIgnoreCase(variant.getProduct().getStatus());
    }

    private boolean requiresBeef(String text) {
        String n = normalizeText(text);
        return n.contains("beefsteak")
                || n.contains("beef steak")
                || n.contains("steak bo")
                || n.contains("bo bit tet")
                || n.contains("thit bo")
                || n.contains("than bo")
                || n.contains("bap bo")
                || n.matches(".*\\bbeef\\b.*");
    }

    private boolean requiresPork(String text) {
        String n = normalizeText(text);
        return n.contains("thit heo")
                || n.contains("thit lon")
                || n.contains("nac dam")
                || n.contains("ba roi")
                || n.contains("suon heo")
                || n.matches(".*\\bpork\\b.*");
    }

    private boolean requiresChicken(String text) {
        String n = normalizeText(text);
        return n.contains("thit ga")
                || n.contains("uc ga")
                || n.contains("dui ga")
                || n.matches(".*\\bchicken\\b.*");
    }

    private boolean isBeefProduct(String name) {
        return requiresBeef(name);
    }

    private boolean isPorkProduct(String name) {
        return requiresPork(name) || containsPorkTerms(name);
    }

    private boolean isChickenProduct(String name) {
        return requiresChicken(name);
    }

    private boolean containsPorkTerms(String text) {
        String n = normalizeText(text);
        return n.contains("heo")
                || n.equals("lon")
                || n.contains(" lon ")
                || n.startsWith("lon ")
                || n.endsWith(" lon")
                || n.contains("nac dam")
                || n.contains("ba roi")
                || n.contains("pork");
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractJson(String text) {
        // Find first { and last } to extract JSON
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    //  FEEDBACK HANDLER (MEMM Loop)
    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    @Transactional
    public boolean handleFeedback(Long messageId, String feedbackType) {
        if (messageId == null || !isSupportedFeedbackType(feedbackType)) {
            log.debug("Ignoring invalid chat feedback: messageId={}, feedbackType={}", messageId, feedbackType);
            return false;
        }

        ChatMessage message = messageRepository.findById(messageId).orElse(null);
        if (message == null || !"ASSISTANT".equals(message.getRole())) {
            log.debug("Ignoring feedback for missing/non-assistant message: {}", messageId);
            return false;
        }

        // Delegate to MemmFeedbackService for comprehensive feedback processing
        memmFeedbackService.processFeedback(message.getUserId(), messageId, feedbackType);
        return true;
    }

    private boolean isSupportedFeedbackType(String feedbackType) {
        return "HELPFUL".equals(feedbackType) || "NOT_HELPFUL".equals(feedbackType);
    }

    private List<UiActionDto> buildUiActions(ChatResponsePayload payload) {
        if (payload.getProposedItems() == null || payload.getProposedItems().isEmpty()) {
            return List.of();
        }
        return payload.getProposedItems().stream()
                .filter(item -> item.getProductId() != null && item.getProductId() > 0)
                .map(item -> UiActionDto.builder()
                        .type("PROPOSED_ITEM")
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .reason(item.getReason())
                        .build())
                .toList();
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    //  CHAT HISTORY
    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    public List<ChatHistoryDto> getChatHistory(Long userId) {
        List<ChatSession> sessions = sessionRepository.findByUser_IdOrderByLastActiveAtDesc(userId);
        List<ChatHistoryDto> result = new ArrayList<>();

        for (ChatSession s : sessions) {
            result.add(ChatHistoryDto.builder()
                    .type("session").sessionId(s.getId())
                    .title("Phiên chat #" + s.getId())
                    .createdAt(s.getCreatedAt().toString())
                    .build());

            List<ChatMessage> messages = messageRepository.findBySession_IdOrderByCreatedAtAsc(s.getId());
            for (ChatMessage m : messages) {
                String displayContent = m.getContent();
                if ("ASSISTANT".equals(m.getRole())) {
                    String jsonStr = extractJson(displayContent);
                    if (jsonStr != null) {
                        try {
                            JsonNode root = objectMapper.readTree(jsonStr);
                            displayContent = root.path("reply").asText(displayContent);
                        } catch (Exception e) {
                            // ignore, fallback to full text
                        }
                    }
                    displayContent = stripProductIds(displayContent);
                }
                
                result.add(ChatHistoryDto.builder()
                        .type("message").id(m.getId()).sessionId(s.getId())
                        .role(m.getRole()).content(displayContent)
                        .createdAt(m.getCreatedAt().toString())
                        .build());
            }
        }
        return result;
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    //  HELPERS
    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    private ChatSession createNewSession(User user) {
        ChatSession session = ChatSession.builder()
                .user(user).status("ACTIVE").interactionCount(0).build();
        return sessionRepository.save(session);
    }

    private List<Map<String, String>> buildConversationHistory(Long sessionId) {
        List<ChatMessage> recentMessages = messageRepository.findTop20BySession_IdOrderByCreatedAtDesc(sessionId);
        Collections.reverse(recentMessages); // chronological order

        return recentMessages.stream()
                .map(m -> Map.of(
                        "role", m.getRole() != null ? m.getRole().toLowerCase(Locale.ROOT) : "user",
                        "content", m.getContent() != null ? m.getContent() : ""))
                .collect(Collectors.toList());
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    //  DTOs
    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    private static class ChatRequestContext {
        private Long sessionId;
        private String userName;
        private int interactionCount;
        private String sessionContext;
        private List<Map<String, String>> conversationHistory;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    private static class SavedAssistantMessage {
        private Long messageId;
        private int interactionCount;
        private String fallbackReply;
        private String replyStatus;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChatResponse {
        private Long sessionId;
        private String aiMessageId;
        private String reply;
        private List<Long> recommendedProductIds;
        private List<ProposedItemDto> proposedItems;
        private List<Long> removeVariantIds;
        private Map<Long, String> removeReasons;
        private Map<Long, String> explanations;
        private Float trustScore;
        private String thoughtProcess;
        private IntentPredictionDto intentPrediction;
        private String expectationPrompt;
        private String replyStatus;
        private String fallbackReply;
        private String streamUrl;
        private List<UiActionDto> uiActions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UiActionDto {
        private String type;
        private Long productId;
        private int quantity;
        private String reason;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class IntentPredictionDto {
        private String detectedIntent;
        private String message;
        private List<SmartSuggestionDto> smartSuggestions;
        private String bundleActionUi;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SmartSuggestionDto {
        private Long itemId;
        private String itemName;
        private String actionUi;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProposedItemDto {
        private Long productId;
        private int quantity;
        private String reason;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    private static class MealOption {
        private int optionNo;
        private String title;
        private List<String> ingredients;
        private String reason;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChatHistoryDto {
        private String type;
        private Long sessionId;
        private Long id;
        private String title;
        private String role;
        private String content;
        private String createdAt;
    }

    @Data
    private static class MotivationContext {
        private String healthGoals;
        private String allergies;
        private String dietaryPreference;
        private Float bmi;
        private String cartContext;
        private boolean hasCartConflicts;
        private List<Long> conflictingVariantIds;
        private String inventorySummary;
        private List<ProductNode> discoveredProducts;
        private boolean hasPromotions;
    }

    @Data
    private static class ChatResponsePayload {
        private String reply;
        private String intentDetected;
        private Float trustScore;
        private String thoughtProcess;
        private IntentPredictionDto intentPrediction;
        private List<Long> recommendedProductIds = new ArrayList<>();
        private List<ProposedItemDto> proposedItems = new ArrayList<>();
        private List<Long> removeVariantIds = new ArrayList<>();
        private Map<Long, String> removeReasons = new HashMap<>();
        private Map<Long, String> explanations = new HashMap<>();
    }
}




