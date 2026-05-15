package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.dto.*;
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
    private final NeedExtractionService needExtractionService;
    private final ProductCandidateService productCandidateService;
    private final ShoppingActionValidator shoppingActionValidator;
    private final AiAgentTools aiAgentTools;
    private final IngredientComparisonService ingredientComparisonService;
    private final UserProfileConstraintService userProfileConstraintService;
    private final SemanticAllergyGuardService semanticAllergyGuardService;
    private final MealCatalogService mealCatalogService;




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
        NeedExtractionService.NeedAnalysis needAnalysis = needExtractionService.analyze(userMessage);

        if (isAllergyCorrection(userMessage)) {
            ChatResponsePayload payload = buildAllergyCorrectionPayload(userId, userMessage);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        if (shouldAskClarificationForBareShoppingList(userMessage, requestContext.getSessionContext())) {
            List<MealOption> existingOptions = readMealOptions(requestContext.getSessionContext());
            ChatResponsePayload payload = existingOptions.isEmpty()
                    ? buildShoppingClarificationPayload()
                    : buildMealSelectionClarificationPayload(existingOptions);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        if (shouldAskClarificationForMixedNeeds(needAnalysis, userMessage)) {
            ChatResponsePayload payload = buildMixedNeedClarificationPayload(needAnalysis);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        if (isMealOptionSelectionRequest(userMessage, requestContext.getSessionContext())) {
            ChatResponsePayload payload = buildShoppingListFromSelectedMealPayload(userId, userMessage, requestContext.getSessionContext());
            ensureMutableCollections(payload);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            updateShoppingSessionContext(requestContext.getSessionId(), payload, List.of(), userMessage);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        if (isMealAlternativeRequest(userMessage, requestContext.getSessionContext())) {
            String effectiveMealPrompt = effectiveMealOptionsPrompt(userMessage, requestContext.getSessionContext());
            int mealOptionsVariant = nextMealOptionsVariant(effectiveMealPrompt, requestContext.getSessionContext());
            List<MealOption> options = filterMealOptionsByUserProfile(buildMealOptions(effectiveMealPrompt, mealOptionsVariant), userId);
            ChatResponsePayload payload = buildMealOptionsPayload(options);
            updateMealOptionsSessionContext(requestContext.getSessionId(), options, effectiveMealPrompt, mealOptionsVariant);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        if (isDirectProductShoppingRequest(userMessage)) {
            ChatResponsePayload payload = buildShoppingListFromDirectProductRequest(userId, userMessage);
            ensureMutableCollections(payload);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            updateShoppingSessionContext(requestContext.getSessionId(), payload, List.of(), userMessage);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        if (isDirectMealShoppingListRequest(userMessage)) {
            ChatResponsePayload payload = buildShoppingListFromDirectMealRequest(userId, userMessage);
            ensureMutableCollections(payload);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            updateShoppingSessionContext(requestContext.getSessionId(), payload, List.of(), userMessage);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        if (isMealIdeaRequest(userMessage)) {
            int mealOptionsVariant = nextMealOptionsVariant(userMessage, requestContext.getSessionContext());
            List<MealOption> options = filterMealOptionsByUserProfile(buildMealOptions(userMessage, mealOptionsVariant), userId);
            ChatResponsePayload payload = buildMealOptionsPayload(options);
            updateMealOptionsSessionContext(requestContext.getSessionId(), options, userMessage, mealOptionsVariant);
            SavedAssistantMessage saved = saveChatResult(requestContext.getSessionId(), userId, userMessage, payload, null);
            return buildImmediateChatResponse(requestContext.getSessionId(), saved, payload);
        }

        // ── ASYNC AI AGENT ORCHESTRATION ──
        SavedAssistantMessage saved = savePendingAssistantMessage(requestContext.getSessionId(), userId, userMessage);
        
        eventPublisher.publishEvent(new Pass2RequestedEvent(saved.getMessageId(), userId, userMessage));

        return ChatResponse.builder()
                .sessionId(requestContext.getSessionId())
                .aiMessageId(String.valueOf(saved.getMessageId()))
                .reply("Đang xử lý yêu cầu...")
                .replyStatus(AiOrchestrationService.STATUS_PENDING_ORCHESTRATION)
                .streamUrl("/api/v1/ai/chat/messages/" + saved.getMessageId() + "/stream")
                .uiActions(new ArrayList<>())
                .build();
    }

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
                    ? AiOrchestrationService.STATUS_PENDING_ORCHESTRATION 
                    : AiOrchestrationService.STATUS_FALLBACK;

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
            if (AiOrchestrationService.STATUS_PENDING_ORCHESTRATION.equals(replyStatus)) {
                eventPublisher.publishEvent(new Pass2RequestedEvent(aiMsg.getId(), userId, userMessage));
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
                    .replyStatus(AiOrchestrationService.STATUS_FALLBACK)
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
            return toJson(snapshot);
        } catch (Exception e) {
            log.warn("Could not serialize validated AI action snapshot: {}", e.getMessage());
            return "{}";
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

    private String buildMemmSystemPrompt(String userName, int interactionCount, MotivationContext ctx) {
        if (this.memmPromptTemplate == null) {
            return "ERROR: PROMPT TEMPLATE NOT LOADED";
        }

        return this.memmPromptTemplate
                .replace("{{USER_NAME}}", userName != null ? userName : "Khách hàng")
                .replace("{{INTERACTION_COUNT}}", String.valueOf(interactionCount))
                .replace("{{BMI}}", ctx.getBmi() != null ? String.valueOf(ctx.getBmi()) : "Không rõ")
                .replace("{{HEALTH_GOALS}}", ctx.getHealthGoals() != null ? ctx.getHealthGoals() : "Không có")
                .replace("{{DIETARY_PREFERENCE}}", ctx.getDietaryPreference() != null ? ctx.getDietaryPreference() : "Không có")
                .replace("{{ALLERGIES}}", ctx.getAllergies() != null ? ctx.getAllergies() : "Không có")
                .replace("{{INVENTORY_SUMMARY}}", buildInventorySummary(ctx.getDiscoveredProducts()))
                .replace("{{CART_CONTEXT}}", ctx.getCartContext() != null ? ctx.getCartContext() : "Trống");
    }

    private String appendAgentSessionContext(String basePrompt, String sessionContext) {
        List<MealOption> options = readMealOptions(sessionContext);
        if (options.isEmpty()) {
            return basePrompt + "\n\nAGENT WORKING MEMORY:\n- lastMealOptions: []\n";
        }

        String optionsText = options.stream()
                .map(option -> option.getOptionNo() + ". " + option.getTitle()
                        + " | ingredients: " + String.join(", ", option.getIngredients())
                        + " | reason: " + (option.getReason() != null ? option.getReason() : ""))
                .collect(Collectors.joining("\n"));
        return basePrompt
                + "\n\nAGENT WORKING MEMORY:\n"
                + "- lastMealOptions are currently visible to the user. If the user asks about one option, answer using these exact titles and ingredients.\n"
                + "- If the user asks for another set of meals, call suggest_meals and replace this memory.\n"
                + "- If the user selects an option, call select_meal with the exact optionNo.\n"
                + optionsText + "\n";
    }

    private List<MealOption> parseMealOptionsFromToolArgs(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(arguments);
            JsonNode optionsNode = root.path("options");
            if (!optionsNode.isArray()) {
                return List.of();
            }
            List<MealOption> options = new ArrayList<>();
            int fallbackNo = 1;
            for (JsonNode optionNode : optionsNode) {
                String title = optionNode.path("title").asText("").trim();
                List<String> ingredients = new ArrayList<>();
                JsonNode ingredientNodes = optionNode.path("ingredients");
                if (ingredientNodes.isArray()) {
                    ingredientNodes.forEach(node -> {
                        String ingredient = node.asText("").trim();
                        if (!ingredient.isBlank()) {
                            ingredients.add(ingredient);
                        }
                    });
                }
                if (title.isBlank() || ingredients.isEmpty()) {
                    continue;
                }
                int optionNo = optionNode.path("optionNo").asInt(fallbackNo);
                options.add(MealOption.builder()
                        .optionNo(optionNo > 0 ? optionNo : fallbackNo)
                        .title(title)
                        .ingredients(ingredients.stream().distinct().toList())
                        .reason(optionNode.path("reason").asText("phù hợp với ngữ cảnh hiện tại"))
                        .build());
                fallbackNo++;
                if (options.size() >= 5) {
                    break;
                }
            }
            return options;
        } catch (Exception e) {
            log.warn("Could not parse suggest_meals arguments: {}", e.getMessage());
            return List.of();
        }
    }

    private void mergeSelectedMealToolPayload(ChatResponsePayload payload, ChatResponsePayload selectedMealToolPayload) {
        if (selectedMealToolPayload == null) {
            return;
        }
        ensureMutableCollections(selectedMealToolPayload);
        if (payload.getProposedItems() == null || payload.getProposedItems().isEmpty()) {
            payload.setProposedItems(new ArrayList<>(selectedMealToolPayload.getProposedItems()));
        }
        if (payload.getRecommendedProductIds() == null || payload.getRecommendedProductIds().isEmpty()) {
            payload.setRecommendedProductIds(new ArrayList<>(selectedMealToolPayload.getRecommendedProductIds()));
        }
        if (payload.getExplanations() == null || payload.getExplanations().isEmpty()) {
            payload.setExplanations(new HashMap<>(selectedMealToolPayload.getExplanations()));
        }
        if (payload.getIntentDetected() == null || "CHAT".equals(payload.getIntentDetected())) {
            payload.setIntentDetected(selectedMealToolPayload.getIntentDetected());
        }
        if (payload.getReply() == null || payload.getReply().isBlank()
                || normalizeText(payload.getReply()).contains("xin loi")) {
            payload.setReply(selectedMealToolPayload.getReply());
        }
        if (payload.getTrustScore() == null) {
            payload.setTrustScore(selectedMealToolPayload.getTrustScore());
        }
    }

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
            // Response is plain text — still clean IDs before returning
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
        if (options == null || options.isEmpty()) {
            ChatResponsePayload emptyPayload = new ChatResponsePayload();
            emptyPayload.setIntentDetected("MEAL_OPTIONS");
            emptyPayload.setTrustScore(70f);
            emptyPayload.setRecommendedProductIds(new ArrayList<>());
            emptyPayload.setProposedItems(new ArrayList<>());
            emptyPayload.setRemoveVariantIds(new ArrayList<>());
            emptyPayload.setRemoveReasons(new HashMap<>());
            emptyPayload.setExplanations(new HashMap<>());
            emptyPayload.setReply("Mình chưa tìm được thực đơn phù hợp sau khi áp dụng các ràng buộc trong hồ sơ của bạn. Bạn có thể nới điều kiện hoặc nói rõ món muốn ăn hơn không?");
            return emptyPayload;
        }
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

    private boolean shouldAskClarificationForMixedNeeds(NeedExtractionService.NeedAnalysis analysis, String userMessage) {
        if (analysis == null || isShoppingListRequest(userMessage)) {
            return false;
        }
        long actionableNeeds = analysis.needs().stream()
                .filter(need -> need != NeedExtractionService.Need.DIRECT_PRODUCT)
                .filter(need -> need != NeedExtractionService.Need.DIRECT_RECIPE)
                .count();
        return actionableNeeds >= 2;
    }

    private ChatResponsePayload buildMixedNeedClarificationPayload(NeedExtractionService.NeedAnalysis analysis) {
        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setIntentDetected("MIXED_NEED_CLARIFICATION");
        payload.setTrustScore(76f);
        payload.setRecommendedProductIds(new ArrayList<>());
        payload.setProposedItems(new ArrayList<>());
        payload.setRemoveVariantIds(new ArrayList<>());
        payload.setRemoveReasons(new HashMap<>());
        payload.setExplanations(new HashMap<>());
        payload.setRecommendedProductIds(productCandidateService.findCandidatesForNeeds(analysis).stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(8)
                .collect(Collectors.toCollection(ArrayList::new)));

        List<String> choices = new ArrayList<>();
        if (analysis.hasNeed(NeedExtractionService.Need.FOOD_MEAL)) {
            choices.add("gợi ý món ăn nhanh");
        }
        if (analysis.hasNeed(NeedExtractionService.Need.DRINK)) {
            choices.add("gợi ý đồ uống giải khát");
        }
        if (analysis.hasNeed(NeedExtractionService.Need.SNACK_SWEET)) {
            choices.add("gợi ý đồ ngọt hoặc ăn vặt");
        }
        if (analysis.hasNeed(NeedExtractionService.Need.HOUSEHOLD_CLEANING)) {
            choices.add("tạo danh sách đồ vệ sinh nhà cửa");
        }
        if (analysis.hasNeed(NeedExtractionService.Need.DISHWASHING)) {
            choices.add("tìm nước rửa chén");
        }
        if (analysis.hasNeed(NeedExtractionService.Need.LAUNDRY)) {
            choices.add("tìm nước giặt");
        }

        String budgetNote = analysis.hasConstraint(NeedExtractionService.Constraint.LOW_BUDGET)
                ? " Mình sẽ ưu tiên phương án tiết kiệm."
                : "";
        payload.setReply("Mình thấy bạn đang nhắc nhiều nhu cầu cùng lúc: "
                + String.join(", ", choices)
                + "." + budgetNote
                + " Bạn muốn mình xử lý mục nào trước?");
        return payload;
    }

    private int nextMealOptionsVariant(String userMessage, String sessionContext) {
        Map<String, Object> context = readSessionContextMap(sessionContext);
        String currentGoal = needExtractionService.mealGoalSignature(userMessage);
        String lastGoal = context.get("lastMealOptionsGoal") != null
                ? String.valueOf(context.get("lastMealOptionsGoal"))
                : "";
        int lastVariant = 0;
        Object rawVariant = context.get("lastMealOptionsVariant");
        if (rawVariant instanceof Number number) {
            lastVariant = number.intValue();
        } else if (rawVariant != null) {
            try {
                lastVariant = Integer.parseInt(String.valueOf(rawVariant));
            } catch (NumberFormatException ignored) {
                lastVariant = 0;
            }
        }
        return currentGoal.equals(lastGoal) ? Math.floorMod(lastVariant + 1, 3) : 0;
    }

    private String mealGoalSignature(String userMessage) {
        return needExtractionService.mealGoalSignature(userMessage);
    }

    private List<MealOption> filterMealOptionsByUserProfile(List<MealOption> options, Long userId) {
        Set<String> avoidanceTokens = readUserAllergyTokens(userId);
        if (options == null || options.isEmpty() || avoidanceTokens.isEmpty()) {
            return options == null ? List.of() : options;
        }
        List<MealOption> safeOptions = options.stream()
                .filter(option -> !mealOptionViolatesProfile(option, avoidanceTokens))
                .toList();
        if (safeOptions.size() == options.size()) {
            return options;
        }
        List<MealOption> renumbered = new ArrayList<>();
        for (int i = 0; i < safeOptions.size(); i++) {
            MealOption option = safeOptions.get(i);
            renumbered.add(MealOption.builder()
                    .optionNo(i + 1)
                    .title(option.getTitle())
                    .ingredients(option.getIngredients())
                    .reason(option.getReason())
                    .build());
        }
        return renumbered;
    }

    private boolean mealOptionViolatesProfile(MealOption option, Set<String> avoidanceTokens) {
        if (option == null) {
            return true;
        }
        String optionText = String.join(" ",
                option.getTitle() != null ? option.getTitle() : "",
                option.getReason() != null ? option.getReason() : "",
                option.getIngredients() != null ? String.join(" ", option.getIngredients()) : "");
        return violatesAllergy(optionText, avoidanceTokens);
    }

    private boolean isMealAlternativeRequest(String userMessage, String sessionContext) {
        if (readMealOptions(sessionContext).isEmpty()) {
            return false;
        }
        String n = normalizeText(userMessage);
        return n.contains("khac") || n.contains("doi mon") || n.contains("thay doi") || 
               n.contains("alternative") || n.contains("them") || n.contains("nua") || 
               n.contains("khong thich") || n.contains("khong ung");
    }

    private String effectiveMealOptionsPrompt(String userMessage, String sessionContext) {
        if (!isMealAlternativeRequest(userMessage, sessionContext)) {
            return userMessage;
        }
        Object lastGoal = readSessionContextMap(sessionContext).get("lastMealOptionsGoal");
        String goal = lastGoal != null ? String.valueOf(lastGoal) : "";
        return switch (goal) {
            case "MEAL_WITH_COFFEE" -> "gợi ý món ăn đi kèm cà phê";
            case "BREAKFAST" -> "gợi ý bữa sáng";
            case "DINNER" -> "gợi ý bữa tối";
            case "VEGETARIAN" -> "gợi ý món ăn chay";
            case "HEALTHY" -> "gợi ý món ăn healthy";
            default -> userMessage;
        };
    }

    private List<MealOption> buildMealOptions(String userMessage, int variant) {
        try {
            List<MealCatalogService.CatalogMealOption> catalogOptions = mealCatalogService.suggestMealOptions(
                    userMessage,
                    variant,
                    Set.of(),
                    3
            );
            if (catalogOptions != null && !catalogOptions.isEmpty()) {
                return catalogOptions.stream()
                        .map(option -> MealOption.builder()
                                .optionNo(option.optionNo())
                                .title(option.title())
                                .ingredients(option.ingredients())
                                .reason(option.reason())
                                .build())
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Meal catalog suggestion failed, using deterministic fallback: {}", e.getMessage());
        }

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

        int normalizedVariant = Math.floorMod(variant, 3);
        if (normalizedVariant == 1) {
            return List.of(
                    MealOption.builder()
                            .optionNo(1)
                            .title("Yến mạch sữa hạnh nhân + blueberry")
                            .ingredients(List.of("yến mạch", "sữa hạnh nhân", "blueberry"))
                            .reason("nhẹ bụng, hợp khi uống cùng cà phê và vẫn đủ chất xơ")
                            .build(),
                    MealOption.builder()
                            .optionNo(2)
                            .title("Trứng luộc + khoai lang + dưa leo")
                            .ingredients(List.of("trứng", "khoai lang", "dưa leo"))
                            .reason("no lâu, ít dầu mỡ, không bị quá ngọt khi đi kèm cà phê")
                            .build(),
                    MealOption.builder()
                            .optionNo(3)
                            .title("Đậu hũ áp chảo + nấm + rau xanh")
                            .ingredients(List.of("đậu hũ", "nấm", "rau xanh"))
                            .reason("protein thực vật, nhẹ bụng, đổi vị so với món gà")
                            .build()
            );
        }

        if (normalizedVariant == 2) {
            return List.of(
                    MealOption.builder()
                            .optionNo(1)
                            .title("Salad trứng + xà lách + dưa leo")
                            .ingredients(List.of("trứng", "xà lách", "dưa leo"))
                            .reason("tươi, nhanh, phù hợp khi muốn ăn nhẹ cùng cà phê")
                            .build(),
                    MealOption.builder()
                            .optionNo(2)
                            .title("Khoai lang + sữa hạnh nhân + táo")
                            .ingredients(List.of("khoai lang", "sữa hạnh nhân", "táo"))
                            .reason("có tinh bột tốt và vị ngọt tự nhiên, không quá nặng bụng")
                            .build(),
                    MealOption.builder()
                            .optionNo(3)
                            .title("Đậu hũ sốt nấm + su hào luộc")
                            .ingredients(List.of("đậu hũ", "nấm", "su hào"))
                            .reason("ấm bụng, ít calo, dùng được cho bữa nhẹ hoặc bữa tối")
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

        // 1. Remove Markdown-style product links: [**Name**](product:123) → **Name**
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
        if (shouldUsePreviousShoppingCandidates(userMessage, sessionContext)) {
            readActiveShoppingCandidateIds(sessionContext).forEach(candidateIds::add);
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

        Set<Long> allowedIds = buildAllowedShoppingCandidateIds(payload, userMessage, sessionContext, discoveredProducts);
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
            String userMessage,
            String sessionContext,
            List<ProductNode> discoveredProducts
    ) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (shouldUsePreviousShoppingCandidates(userMessage, sessionContext)) {
            readActiveShoppingCandidateIds(sessionContext).forEach(ids::add);
        }
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

    private boolean shouldUsePreviousShoppingCandidates(String userMessage, String sessionContext) {
        if (readActiveShoppingCandidateIds(sessionContext).isEmpty()) {
            return false;
        }
        String n = normalizeText(userMessage);
        boolean confirmation = n.equals("ok")
                || n.equals("okay")
                || n.equals("dong y")
                || n.equals("co")
                || n.equals("tao di")
                || n.equals("chot")
                || n.equals("chot danh sach")
                || n.equals("them het")
                || n.equals("them tat ca")
                || n.contains("chot danh sach nay")
                || n.contains("them het vao gio")
                || n.contains("them tat ca vao gio")
                || n.contains("tao danh sach cho cac nguyen lieu nay");
        return confirmation || isModificationRequest(userMessage);
    }

    private boolean isShoppingListRequest(String userMessage) {
        return needExtractionService.isShoppingListRequest(userMessage);
    }

    private boolean isDirectMealShoppingListRequest(String userMessage) {
        String n = normalizeText(userMessage);
        if (!isShoppingListRequest(userMessage)) {
            return false;
        }
        if (needExtractionService.recipeKey(userMessage).isPresent()) {
            return true;
        }
        return n.contains("mon ")
                || n.contains("nguyen lieu")
                || n.contains("cong thuc")
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
        if (isMealOptionSelectionRequest(userMessage, null)) {
            return false;
        }
        if (n.contains("danh sach mua") || n.contains("mua sam")
                || n.contains("them vao gio") || n.contains("cho vao gio") || n.contains("bo vao gio")) {
            return false;
        }
        boolean coffeePairingIntent = isCoffeeMealPairingIntent(userMessage);
        if (!isMealOrDietIntent(userMessage) && !coffeePairingIntent) {
            return false;
        }
        return coffeePairingIntent
                || n.contains("goi y")
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

    private boolean isCoffeeMealPairingIntent(String userMessage) {
        return needExtractionService.isCoffeeMealPairingIntent(userMessage);
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

    private ChatResponsePayload buildShoppingListFromDirectProductRequest(Long userId, String userMessage) {
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
        boolean hadAvailableItemsBeforeProfileFilter = !payload.getProposedItems().isEmpty();
        enforceUserProfileAllergies(payload, userId);
        if (hadAvailableItemsBeforeProfileFilter && payload.getProposedItems().isEmpty()) {
            payload.setTrustScore(65f);
            payload.setReply("Sản phẩm bạn yêu cầu trùng với dị ứng hoặc ràng buộc ăn uống trong hồ sơ nên mình không thêm vào danh sách.");
            return payload;
        }
        if (payload.getProposedItems().isEmpty()) {
            payload.setTrustScore(65f);
            payload.setReply("Mình tìm thấy sản phẩm bạn yêu cầu nhưng hiện chưa có hàng. Bạn muốn mình gợi ý sản phẩm thay thế không?");
        } else {
            payload.setReply("Mình đã tạo danh sách mua sắm từ các sản phẩm bạn yêu cầu.");
        }
        return payload;
    }

    private List<Product> findExplicitlyRequestedProducts(String userMessage) {
        return productCandidateService.findExplicitlyRequestedProducts(userMessage);
    }

    private List<String> extractDirectProductPhrases(String userMessage) {
        return productCandidateService.extractDirectProductPhrases(userMessage);
    }

    private boolean directProductMatchesPhrase(Product product, String phrase) {
        return productCandidateService.directProductMatchesPhrase(product, phrase);
    }

    private ChatResponsePayload buildShoppingListFromDirectMealRequest(Long userId, String userMessage) {
        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setIntentDetected("DIRECT_MEAL_SHOPPING_LIST");
        payload.setTrustScore(82f);
        payload.setRecommendedProductIds(new ArrayList<>());
        payload.setProposedItems(new ArrayList<>());
        payload.setRemoveVariantIds(new ArrayList<>());
        payload.setRemoveReasons(new HashMap<>());
        payload.setExplanations(new HashMap<>());

        Optional<MealOption> knownRecipe = buildKnownRecipeTemplate(userMessage);
        List<String> ingredientTerms = knownRecipe
                .map(MealOption::getIngredients)
                .orElseGet(() -> extractDirectIngredientTerms(userMessage));
        Set<String> allergyTokens = readUserAllergyTokens(userId);
        List<String> allergyExcludedTerms = ingredientTerms.stream()
                .filter(term -> violatesAllergy(term, allergyTokens))
                .toList();
        if (!allergyExcludedTerms.isEmpty()) {
            ingredientTerms = ingredientTerms.stream()
                    .filter(term -> !violatesAllergy(term, allergyTokens))
                    .toList();
        }
        if (ingredientTerms.isEmpty()) {
            payload.setIntentDetected("MEAL_SELECTION_CLARIFICATION");
            payload.setTrustScore(70f);
            if (!allergyExcludedTerms.isEmpty()) {
                payload.setReply("Món này đang trùng toàn bộ với dị ứng trong hồ sơ của bạn: "
                        + String.join(", ", allergyExcludedTerms)
                        + ". Bạn muốn mình gợi ý phiên bản thay thế không chứa dị ứng không?");
                return payload;
            }
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
        List<String> safeIngredientTerms = ingredientTerms;
        MealOption directMeal = knownRecipe.orElseGet(() -> MealOption.builder()
                .optionNo(0)
                .title(extractDirectMealTitle(userMessage))
                .ingredients(safeIngredientTerms)
                .reason("nguyên liệu do người dùng nêu trực tiếp")
                .build());

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
            enforceUserProfileAllergies(payload, userId);
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

        String recipeTitle = directMeal.getTitle();
        String fullIngredientsText = String.join(", ", ingredientTerms);
        if (missingTerms.isEmpty()) {
            payload.setReply("Mình đã tạo danh sách mua sắm cho " + recipeTitle
                    + ". Nguyên liệu gồm: " + fullIngredientsText + ".");
        } else {
            payload.setReply("Mình đã tạo danh sách mua sắm cho " + recipeTitle
                    + ". Công thức cần: " + fullIngredientsText
                    + ". Hiện chưa khớp được hoặc chưa còn hàng: "
                    + String.join(", ", missingTerms) + ".");
            payload.setTrustScore(72f);
        }
        if (!allergyExcludedTerms.isEmpty()) {
            payload.setReply(payload.getReply()
                    + " Mình đã loại khỏi danh sách các nguyên liệu trùng dị ứng trong hồ sơ của bạn: "
                    + String.join(", ", allergyExcludedTerms) + ".");
            payload.setTrustScore(payload.getTrustScore() == null ? 74f : Math.min(payload.getTrustScore(), 74f));
        }
        return payload;
    }

    private Optional<MealOption> buildKnownRecipeTemplate(String userMessage) {
        Optional<String> recipeKey = needExtractionService.recipeKey(userMessage);
        if (recipeKey.isPresent() && "GA_KHO".equals(recipeKey.get())) {
            return Optional.of(MealOption.builder()
                    .optionNo(0)
                    .title("Gà kho")
                    .ingredients(List.of("thịt gà", "nước mắm", "hạt nêm", "đường", "tiêu", "hành lá", "tỏi"))
                    .reason("công thức gà kho cơ bản")
                    .build());
        }
        if (recipeKey.isPresent() && "SALAD_HEALTHY".equals(recipeKey.get())) {
            return Optional.of(MealOption.builder()
                    .optionNo(0)
                    .title("Salad healthy")
                    .ingredients(List.of("xà lách", "dưa leo", "cà chua", "trứng", "ức gà", "dầu oliu"))
                    .reason("salad cân bằng rau xanh, protein và chất béo tốt")
                    .build());
        }
        if (recipeKey.isPresent() && "MI_Y".equals(recipeKey.get())) {
            return Optional.of(MealOption.builder()
                    .optionNo(0)
                    .title("Mì Ý sốt kem nấm không cà chua")
                    .ingredients(List.of("mì Ý", "nấm", "sữa tươi", "phô mai", "ức gà", "dầu oliu"))
                    .reason("phiên bản mì Ý không dùng cà chua, phù hợp khi cần tránh sốt cà chua")
                    .build());
        }
        return Optional.empty();
    }

    private List<String> extractDirectIngredientTerms(String userMessage) {
        String n = normalizeText(userMessage);
        List<String> knownTerms = List.of(
                "uc ga", "thit ga", "ga", "trung", "dau hu", "dau phu",
                "mang tay", "khoai lang", "xa lach", "dua leo", "tao",
                "yen mach", "nam", "su hao", "gao lut", "bun gao lut",
                "sua tuoi tach beo", "sua tuoi", "sua hanh nhan", "blueberry",
                "rau xanh", "bap cai", "bong cai", "ca rot", "bi do",
                "thit bo", "bo", "thit heo nac", "heo nac",
                "nuoc mam", "hat nem", "duong", "tieu", "hanh la",
                "hanh tim", "toi", "ca chua", "dau oliu", "dau an",
                "mi y", "spaghetti", "pasta", "pho mai"
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

    private ChatResponsePayload buildShoppingListFromSelectedMealPayload(Long userId, String userMessage, String sessionContext) {
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

        List<IngredientComparisonService.IngredientMatchResult> matchResults = 
                ingredientComparisonService.analyzeAndMatchIngredients(selected.getIngredients());

        List<String> skippedStaples = new ArrayList<>();
        List<String> missingIngredients = new ArrayList<>();
        Set<String> selectedMealAllergyTokens = readUserAllergyTokens(userId);

        // Batch load product details with categories to avoid N+1 and LazyInitializationException
        List<Long> matchedIds = matchResults.stream()
                .map(IngredientComparisonService.IngredientMatchResult::productId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Product> productDetailsMap = productRepository.findAllByIdWithCategory(matchedIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        for (IngredientComparisonService.IngredientMatchResult match : matchResults) {
            if (violatesAllergy(match.originalIngredient(), selectedMealAllergyTokens)) {
                missingIngredients.add(match.originalIngredient() + " (trùng dị ứng trong hồ sơ)");
                continue;
            }
            if (match.status().equals("MATCHED") || match.status().equals("AMBIGUOUS")) {
                Product product = productDetailsMap.get(match.productId());
                if (product != null && !isStrictIngredientProductCompatible(match.originalIngredient(), product)) {
                    missingIngredients.add(match.originalIngredient() + " (sản phẩm khớp sai loại: " + product.getName() + ")");
                    continue;
                }
                if (product != null && productViolatesAllergy(product, selectedMealAllergyTokens)) {
                    missingIngredients.add(match.originalIngredient() + " (sản phẩm trùng dị ứng: " + product.getName() + ")");
                    continue;
                }
                if (product != null && Boolean.TRUE.equals(product.getIsStaple())) {
                    skippedStaples.add(product.getName());
                    continue;
                }
                
                if (product != null && stockedProductIds.contains(product.getId()) && !usedProductIds.contains(product.getId())) {
                    usedProductIds.add(product.getId());
                    payload.getProposedItems().add(ProposedItemDto.builder()
                            .productId(product.getId())
                            .quantity(1)
                            .reason("Nguyên liệu " + match.originalIngredient() + " cho " + selected.getTitle() + ".")
                            .build());
                    payload.getExplanations().put(product.getId(), "Khớp Graph: " + match.details());
                } else {
                    missingIngredients.add(match.originalIngredient() + " (hết hàng)");
                }
            } else {
                missingIngredients.add(match.originalIngredient());
            }
        }

        syncRecommendedIdsFromProposedItems(payload);
        
        StringBuilder replyBuilder = new StringBuilder();
        replyBuilder.append("Hệ thống đã phân tích và đối chiếu nguyên liệu cho món **").append(selected.getTitle()).append("**: \n\n");
        
        if (payload.getProposedItems().isEmpty()) {
            replyBuilder.append("⚠️ Rất tiếc, mình chưa tìm thấy nguyên liệu chính nào còn hàng trong kho phù hợp với thực đơn này.");
            payload.setReply(replyBuilder.toString());
            payload.setTrustScore(50f);
            return payload;
        }

        replyBuilder.append("✅ Đã chuẩn bị danh sách mua sắm với các nguyên liệu chính.");
        
        if (!skippedStaples.isEmpty()) {
            replyBuilder.append("\n- Lược bỏ gia vị có sẵn: ").append(String.join(", ", skippedStaples)).append(".");
        }
        
        if (!missingIngredients.isEmpty()) {
            replyBuilder.append("\n- Lưu ý: Không tìm thấy hoặc hết hàng cho: ").append(String.join(", ", missingIngredients)).append(".");
        }

        payload.setReply(replyBuilder.toString());
        return payload;
    }

    private boolean isStrictIngredientProductCompatible(String ingredient, Product product) {
        String n = normalizeText(ingredient);
        String productText = productSearchText(product);
        if (containsNormalizedPhrase(n, "uc ga")) {
            boolean isBreast = containsNormalizedPhrase(productText, "uc ga")
                    || (containsNormalizedPhrase(productText, "phi le") && containsNormalizedPhrase(productText, "ga"));
            boolean wrongPart = containsNormalizedPhrase(productText, "canh ga")
                    || containsNormalizedPhrase(productText, "dui ga")
                    || containsNormalizedPhrase(productText, "chan ga")
                    || containsNormalizedPhrase(productText, "long ga");
            return isBreast && !wrongPart;
        }
        if (containsNormalizedPhrase(n, "canh ga")) {
            return containsNormalizedPhrase(productText, "canh ga");
        }
        if (containsNormalizedPhrase(n, "dui ga")) {
            return containsNormalizedPhrase(productText, "dui ga");
        }
        return true;
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
                .filter(product -> isIngredientMappedProductAllowed(ingredient, selected, product, scoringContext))
                .max(Comparator.comparingInt(product -> ingredientProductScore(product, aliases, scoringContext)));
    }

    private boolean isIngredientMappedProductAllowed(
            String ingredient,
            MealOption selected,
            Product product,
            String scoringContext
    ) {
        if (selected != null && selected.getOptionNo() == 0) {
            return true;
        }
        if (isRecipeSeasoningIngredient(ingredient)) {
            return true;
        }
        return isMealCandidateAllowed(
                scoringContext,
                product.getName(),
                safeCategoryName(product),
                product.getDescription()
        );
    }

    private boolean isRecipeSeasoningIngredient(String ingredient) {
        String n = normalizeText(ingredient);
        return containsNormalizedPhrase(n, "nuoc mam")
                || containsNormalizedPhrase(n, "hat nem")
                || containsNormalizedPhrase(n, "duong")
                || containsNormalizedPhrase(n, "tieu")
                || containsNormalizedPhrase(n, "hanh la")
                || containsNormalizedPhrase(n, "hanh tim")
                || containsNormalizedPhrase(n, "toi")
                || containsNormalizedPhrase(n, "dau oliu")
                || containsNormalizedPhrase(n, "dau an");
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
        if (n.contains("thit ga") || n.equals("ga")) return List.of("thit ga", "ga", "dui ga", "canh ga", "uc ga");
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
        if (n.contains("sua tuoi")) return List.of("sua tuoi");
        if (n.contains("sua hanh nhan")) return List.of("sua hanh nhan", "hanh nhan");
        if (n.contains("blueberry")) return List.of("blueberry", "viet quat");
        if (n.contains("rau xanh")) return List.of("rau xanh", "rau", "xa lach", "bap cai", "mong toi");
        if (n.contains("mi y") || n.contains("spaghetti") || n.contains("pasta")) return List.of("mi y", "spaghetti", "pasta");
        if (n.contains("pho mai")) return List.of("pho mai", "cheese");
        if (n.contains("ca chua")) return List.of("ca chua", "tomato");
        if (n.contains("nuoc mam")) return List.of("nuoc mam");
        if (n.contains("hat nem")) return List.of("hat nem");
        if (n.equals("duong") || n.contains(" duong")) return List.of("duong");
        if (n.contains("tieu")) return List.of("tieu", "tieu den");
        if (n.contains("hanh la")) return List.of("hanh la");
        if (n.contains("hanh tim")) return List.of("hanh tim", "hanh");
        if (n.equals("toi") || n.contains(" toi")) return List.of("toi");
        if (n.contains("dau oliu")) return List.of("dau oliu", "dau xit oliu", "olive oil");
        if (n.contains("dau an")) return List.of("dau an", "dau an huong duong");
        return List.of(n);
    }

    private boolean shouldAskClarificationForBareShoppingList(String userMessage, String sessionContext) {
        if (!isShoppingListRequest(userMessage) || isMealOrDietIntent(userMessage)) {
            return false;
        }
        if (!readActiveShoppingCandidateIds(sessionContext).isEmpty()
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
            if (isShoppingListRequest(userMessage)) {
                clearShoppingSessionContext(sessionId);
            }
            return;
        }

        Set<Long> stockedIds = findActiveStockedProductIds(ids);
        List<Long> safeIds = ids.stream()
                .filter(stockedIds::contains)
                .limit(12)
                .toList();
        if (safeIds.isEmpty()) {
            if (isShoppingListRequest(userMessage)) {
                clearShoppingSessionContext(sessionId);
            }
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
            context.remove("lastMealOptions"); // Clear meal options once a shopping list context is established
            context.remove("lastMealOptionsGoal");
            context.remove("lastMealOptionsVariant");
            try {
                session.setSessionContext(toJson(context));
                sessionRepository.save(session);
            } catch (Exception e) {
                log.warn("Could not update shopping session context: {}", e.getMessage());
            }
        });
    }

    private void updateMealOptionsSessionContext(Long sessionId, List<MealOption> options, String userMessage, int variant) {
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
            context.put("lastMealOptionsGoal", mealGoalSignature(userMessage));
            context.put("lastMealOptionsVariant", variant);
            context.put("lastMealOptionsUpdatedAt", LocalDateTime.now().toString());
            context.remove("lastShoppingCandidateIds");
            context.remove("lastShoppingCandidateMealIntent");
            try {
                session.setSessionContext(toJson(context));
                sessionRepository.save(session);
                logFrozenMealOptions("updateMealOptionsSessionContext", sessionId, options);
            } catch (Exception e) {
                log.warn("Could not update meal options session context: {}", e.getMessage());
            }
        });
    }

    private void clearMealOptionsSessionContext(Long sessionId) {
        transactionTemplate.executeWithoutResult(status -> {
            ChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Chat session not found"));
            Map<String, Object> context = readSessionContextMap(session.getSessionContext());
            context.remove("lastMealOptions");
            context.remove("lastMealOptionsGoal");
            context.remove("lastMealOptionsVariant");
            context.remove("lastMealOptionsUpdatedAt");
            try {
                session.setSessionContext(toJson(context));
                sessionRepository.save(session);
            } catch (Exception e) {
                log.warn("Could not clear meal options session context: {}", e.getMessage());
            }
        });
    }

    private void clearShoppingSessionContext(Long sessionId) {
        transactionTemplate.executeWithoutResult(status -> {
            ChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Chat session not found"));
            Map<String, Object> context = readSessionContextMap(session.getSessionContext());
            context.remove("lastShoppingCandidateIds");
            context.remove("lastShoppingCandidateMealIntent");
            context.remove("lastShoppingCandidateUpdatedAt");
            try {
                session.setSessionContext(toJson(context));
                sessionRepository.save(session);
            } catch (Exception e) {
                log.warn("Could not clear shopping session context: {}", e.getMessage());
            }
        });
    }

    private void logFrozenMealOptions(String source, Long sessionId, List<MealOption> options) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", LocalDateTime.now().toString());
            entry.put("event", "FROZEN_MEAL_OPTIONS");
            entry.put("source", source);
            entry.put("sessionId", sessionId);
            entry.put("options", options == null
                    ? List.of()
                    : options.stream().map(this::mealOptionToMap).toList());
            String logEntry = "\n" + toJson(entry) + "\n";
            java.nio.file.Files.write(
                    java.nio.file.Paths.get("ai-debug.txt"),
                    logEntry.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception e) {
            log.debug("Could not write frozen meal options debug log: {}", e.getMessage());
        }
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

    private List<Long> readActiveShoppingCandidateIds(String sessionContext) {
        List<Long> ids = readShoppingCandidateIds(sessionContext);
        if (ids.isEmpty()) {
            return List.of();
        }
        Set<Long> activeStockedIds = findActiveStockedProductIds(ids);
        if (activeStockedIds == null || activeStockedIds.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(activeStockedIds::contains)
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
        Map<Long, Product> productsById = productRepository.findAllByIdWithCategory(productIds).stream()
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

        Map<Long, Product> productsById = productRepository.findAllByIdWithCategory(productIds).stream()
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
    private void enforceUserProfileAllergies(ChatResponsePayload payload, Long userId) {
        Set<String> allergyTokens = readUserAllergyTokens(userId);
        if (allergyTokens.isEmpty() || payload.getProposedItems() == null || payload.getProposedItems().isEmpty()) {
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

        Map<Long, Product> productsById = productRepository.findAllByIdWithCategory(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        List<String> removedNames = new ArrayList<>();

        payload.getProposedItems().removeIf(item -> {
            Product product = productsById.get(item.getProductId());
            boolean remove = product == null || productViolatesAllergy(product, allergyTokens)
                    || violatesAllergy(payload.getExplanations().get(item.getProductId()), allergyTokens)
                    || violatesAllergy(item.getReason(), allergyTokens);
            if (remove && product != null) {
                removedNames.add(product.getName());
            }
            return remove;
        });

        if (!removedNames.isEmpty()) {
            if (payload.getProposedItems().isEmpty()) {
                payload.setRecommendedProductIds(new ArrayList<>());
            } else {
                syncRecommendedIdsFromProposedItems(payload);
            }
            appendCorrection(payload, "Mình đã loại các sản phẩm trùng dị ứng trong hồ sơ của bạn: "
                    + String.join(", ", removedNames) + ".");
            payload.setTrustScore(payload.getTrustScore() == null ? 70f : Math.min(payload.getTrustScore(), 70f));
            log.info("Allergy Guard: removed products {} for user {}", removedNames, userId);
        }
    }

    private Set<String> readUserAllergyTokens(Long userId) {
        return userProfileConstraintService.loadAvoidanceTerms(userId);
    }

    private Set<String> extractAllergyTokens(String allergies) {
        return userProfileConstraintService.extractAvoidanceTerms(allergies);
    }

    private boolean productViolatesAllergy(Product product, Set<String> allergyTokens) {
        return userProfileConstraintService.violatesProduct(product, allergyTokens);
    }

    private boolean violatesAllergy(String text, Set<String> allergyTokens) {
        return userProfileConstraintService.violatesText(text, allergyTokens);
    }

    private boolean isAllergyCorrection(String userMessage) {
        return !userProfileConstraintService.extractClearedAllergyTerms(userMessage).isEmpty();
    }

    private ChatResponsePayload buildAllergyCorrectionPayload(Long userId, String userMessage) {
        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setIntentDetected("PROFILE_ALLERGY_CORRECTION");
        payload.setTrustScore(92f);
        payload.setRecommendedProductIds(new ArrayList<>());
        payload.setProposedItems(new ArrayList<>());
        payload.setRemoveVariantIds(new ArrayList<>());
        payload.setRemoveReasons(new HashMap<>());
        payload.setExplanations(new HashMap<>());

        Set<String> clearedTerms = userProfileConstraintService.extractClearedAllergyTerms(userMessage);
        transactionTemplate.executeWithoutResult(status ->
                nutritionProfileRepository.findByUser_Id(userId).ifPresent(profile -> {
                    String updated = userProfileConstraintService.removeClearedAllergyTerms(profile.getAllergies(), userMessage);
                    profile.setAllergies(updated);
                    List<String> allowed = clearedTerms.stream()
                            .filter(term -> !"*".equals(term))
                            .toList();
                    if (!allowed.isEmpty()) {
                        profile.setFoodConstraints(userProfileConstraintService.mergeFoodConstraints(
                                profile.getFoodConstraints(),
                                List.of(),
                                allowed,
                                List.of(),
                                List.of()
                        ));
                    }
                    nutritionProfileRepository.save(profile);
                }));

        String displayTerms = clearedTerms.contains("*")
                ? "các dị ứng đã lưu"
                : String.join(", ", clearedTerms);
        payload.setReply("Mình đã cập nhật hồ sơ: bạn không dị ứng với " + displayTerms
                + ". Từ giờ mình sẽ không loại các sản phẩm đó vì lý do dị ứng nữa.");
        return payload;
    }

    private void filterExcludedIngredients(ChatResponsePayload payload, String userMessage) {
        if (!excludesSeafood(userMessage)) return;

        List<Long> productIds = payload.getProposedItems().stream()
                .map(ProposedItemDto::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) return;

        Map<Long, Product> productsById = productRepository.findAllByIdWithCategory(productIds).stream()
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
        Map<Long, Product> productsById = productRepository.findAllByIdWithCategory(productIds).stream()
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
        return needExtractionService.isMealOrDietIntent(userMessage);
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
        return shoppingActionValidator.findActiveStockedProductIds(productIds);
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
    private static class MealOption {
        private int optionNo;
        private String title;
        private List<String> ingredients;
        private String reason;
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

    public ChatResponsePayload orchestratePass1(Long userId, Long sessionId, String userMessage) {
        ChatRequestContext requestContext = prepareChatRequest(userId, sessionId, userMessage);
        MotivationContext motivation = analyzeMotivation(userId, userMessage);
        List<ProductNode> discoveredProducts = new ArrayList<>();
        motivation.setDiscoveredProducts(discoveredProducts);
        
        String systemPrompt = appendAgentSessionContext(
                buildMemmSystemPrompt(requestContext.getUserName(), requestContext.getInteractionCount(), motivation),
                requestContext.getSessionContext()
        );

        int maxIterations = 3;
        int currentIteration = 0;
        boolean isDone = false;
        OpenRouterClient.AiCompletionResult aiResult = null;
        ChatResponsePayload selectedMealToolPayload = null;
        List<Map<String, String>> messages = new ArrayList<>(requestContext.getConversationHistory());

        while (!isDone && currentIteration < maxIterations) {
            aiResult = openRouterClient
                    .chatCompletion(systemPrompt, messages, aiAgentTools.getAvailableTools(), config.getPass1Model(),
                            Duration.ofMillis(pass1TimeoutMs))
                    .block();

            if (aiResult != null && aiResult.getToolCalls() != null && aiResult.getToolCalls().isArray() && !aiResult.getToolCalls().isEmpty()) {
                Map<String, String> assistantMsg = new HashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", aiResult.getReply() != null ? aiResult.getReply() : "");
                assistantMsg.put("tool_calls", aiResult.getToolCalls().toString());
                messages.add(assistantMsg);

                for (JsonNode toolCall : aiResult.getToolCalls()) {
                    String toolCallId = toolCall.path("id").asText();
                    String name = toolCall.path("function").path("name").asText();
                    String arguments = toolCall.path("function").path("arguments").asText();
                    
                    String toolResult;
                    if ("select_meal".equals(name)) {
                        try {
                            JsonNode args = objectMapper.readTree(arguments);
                            int optionNo = args.path("optionNo").asInt();
                            String dummyMsg = "mon so " + optionNo;
                            ChatResponsePayload mealPayload = buildShoppingListFromSelectedMealPayload(userId, dummyMsg, requestContext.getSessionContext());
                            ensureMutableCollections(mealPayload);
                            selectedMealToolPayload = mealPayload;
                            toolResult = toJson(Map.of("status", "selected", "instruction", "Use validated items"));
                        } catch (Exception e) {
                            toolResult = "{\"error\": \"FAILED\", \"message\": \"" + e.getMessage() + "\"}";
                        }
                    } else if ("suggest_meals".equals(name)) {
                        List<MealOption> options = filterMealOptionsByUserProfile(parseMealOptionsFromToolArgs(arguments), userId);
                        updateMealOptionsSessionContext(requestContext.getSessionId(), options, userMessage, 0);
                        toolResult = toJson(Map.of("status", "stored", "count", options.size()));
                    } else if ("clear_context".equals(name)) {
                        clearMealOptionsSessionContext(requestContext.getSessionId());
                        toolResult = "{\"status\":\"cleared\"}";
                    } else {
                        toolResult = aiAgentTools.executeTool(userId, name, arguments);
                    }

                    Map<String, String> toolMsg = new HashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", toolCallId);
                    toolMsg.put("name", name);
                    toolMsg.put("content", toolResult);
                    messages.add(toolMsg);
                }
                currentIteration++;
            } else {
                isDone = true;
            }
        }

        ChatResponsePayload payload = aiResult != null && aiResult.isSuccess()
                ? parseAiResponse(aiResult.getReply())
                : buildFallbackPayload(userMessage, discoveredProducts);
        ensureMutableCollections(payload);
        if (selectedMealToolPayload != null) {
            mergeSelectedMealToolPayload(payload, selectedMealToolPayload);
        }
        
        // Enrichment logic
        enrichWithSpecializedNutrition(payload, userId, userMessage);
        
        return payload;
    }

    private void enrichWithSpecializedNutrition(ChatResponsePayload payload, Long userId, String userMessage) {
        if ("MEAL_PLAN_AUTO".equals(payload.getIntentDetected()) && (payload.getProposedItems() == null || payload.getProposedItems().isEmpty())) {
             try {
                  NutritionChatIntegrator.MealPlanChatResult mealPlan = nutritionChatIntegrator.generateMealPlanViaChat(userId, userMessage);
                  if (mealPlan.isSuccess()) {
                      payload.setReply(payload.getReply() + "\n\nTôi đã tạo một thực đơn 7 ngày mới cho bạn: " + mealPlan.getTitle());
                      for (NutritionChatIntegrator.ProposedItemForChat item : mealPlan.getProposedItems()) {
                          payload.getProposedItems().add(ProposedItemDto.builder()
                                  .productId(item.getProductId())
                                  .quantity(item.getQuantity() != null ? item.getQuantity() : 1)
                                  .reason(item.getReason() != null ? item.getReason() : "Dành cho thực đơn mới")
                                  .build());
                      }
                  }
              } catch (Exception e) {
                  log.warn("Dynamic meal plan generation failed: {}", e.getMessage());
               }
        }
    }

    @Transactional
    public void applyGuardrails(ChatResponsePayload payload, Long userId, String userMessage) {
        ChatRequestContext requestContext = prepareChatRequest(userId, 0L, userMessage); // sessionId not needed for filters usually
        
        if (payload.getProposedItems() != null && !payload.getProposedItems().isEmpty()) {
            enforceProposedItemsCandidateScope(payload, userMessage, requestContext.getSessionContext(), new ArrayList<>());
            ensureProposedItemsForShoppingAction(payload, userMessage, requestContext.getSessionContext(), new ArrayList<>());
            
            enforceRecipeIngredientConsistency(payload, userMessage);
            filterPantryStaples(payload.getProposedItems(), userMessage);
            filterOutOfStock(payload.getProposedItems());
            filterNonFoodForMealIntent(payload, userMessage);
            filterExcludedIngredients(payload, userMessage);
            enforceUserProfileAllergies(payload, userId);
            semanticAllergyGuardService.enforceSemanticGuard(payload, userId);
            filterLowQualityMealItems(payload, userMessage);
            refillProposedItemsIfTooFew(payload, userMessage);
            syncRecommendedIdsFromProposedItems(payload);
        }
        try {
            filterRecommendedProductIds(payload, userMessage);
        } catch (Exception e) {
            log.warn("filterRecommendedProductIds failed: {}", e.getMessage());
        }
    }

    public SavedAssistantMessage savePendingAssistantMessage(Long sessionId, Long userId, String userMessage) {
        return transactionTemplate.execute(status -> {
            ChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Chat session not found"));
            int nextInteractionCount = (session.getInteractionCount() != null ? session.getInteractionCount() : 0) + 1;
            session.setInteractionCount(nextInteractionCount);
            session.setLastActiveAt(LocalDateTime.now());
            sessionRepository.save(session);

            ChatMessage aiMsg = ChatMessage.builder()
                    .session(session).userId(userId).role("ASSISTANT")
                    .content("...")
                    .replyStatus(AiOrchestrationService.STATUS_PENDING_ORCHESTRATION)
                    .build();
            messageRepository.save(aiMsg);

            return SavedAssistantMessage.builder()
                    .messageId(aiMsg.getId())
                    .interactionCount(nextInteractionCount)
                    .replyStatus(aiMsg.getReplyStatus())
                    .build();
        });
    }

    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Could not serialize to JSON: {}", e.getMessage());
            return "{}";
        }
    }
}





