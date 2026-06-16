package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.dto.ChatRequestDto;
import com.smartgrocery.backend.dto.ChatResponseDto;
import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.UserNutritionProfileRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import com.smartgrocery.backend.service.ChatHistoryService;
import com.smartgrocery.backend.service.recommendation.AllergyRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private final CatalogCacheService catalogCacheService;
    private final ShoppingItemBuilder shoppingItemBuilder;
    private final MealIntentService mealIntentService;
    private final DiscountIntentService discountIntentService;
    private final PromptBuilderService promptBuilderService;
    private final OpenRouterClient openRouterClient;

    private final InventoryStockRepository inventoryStockRepository;
    private final UserNutritionProfileRepository nutritionProfileRepository;
    private final UserRepository userRepository;
    private final ChatHistoryService chatHistoryService;
    private final AllergyRules allergyRules;
    private final ProductVariantRepository productVariantRepository;

    private final IntentResolverService intentResolverService;
    private final ConversationStateManager stateManager;
    private final GeneralShoppingIntentService generalShoppingIntentService;

    private static final Pattern SEARCH_TAG_PATTERN = Pattern.compile("\\[SEARCH:\\s*([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
    private static final String SHOW_DISCOUNTS_TAG = "[SHOW_DISCOUNTS]";

    public ChatResponseDto processMessage(User loggedInUser, ChatRequestDto requestDto) {
        long t0 = System.currentTimeMillis();

        // 1. Ensure cache is loaded
        catalogCacheService.ensureCatalogCached();
        List<Meal> allMeals = catalogCacheService.getCachedMeals();
        Map<Long, List<MealIngredient>> ingredientsByMeal = catalogCacheService.getCachedIngredientsByMeal();
        List<ProductVariant> discountedVariants = catalogCacheService.getCachedDiscountedVariants();

        // 2. Fetch inventory and profile details
        List<String> oosProducts = inventoryStockRepository.findOutOfStockProductNames();
        Set<String> oosLower = oosProducts.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        String userProfileStr = "";
        Set<String> allergyKeywords = new HashSet<>();
        if (requestDto.getUserId() != null) {
            Optional<UserNutritionProfile> profileOpt = nutritionProfileRepository.findByUser_Id(requestDto.getUserId());
            if (profileOpt.isPresent()) {
                UserNutritionProfile profile = profileOpt.get();
                userProfileStr = String.format(
                    "\n- Chế độ ăn kiêng: %s\n- Dị ứng (TUYỆT ĐỐI TRÁNH): %s\n- Mục tiêu sức khỏe: %s\n- BMI: %s\n- Calo/ngày: %s kcal\n",
                    profile.getDietaryPreference() != null ? profile.getDietaryPreference() : "Không có",
                    profile.getAllergies() != null ? profile.getAllergies() : "Không có",
                    profile.getHealthGoals() != null ? profile.getHealthGoals() : "Không có",
                    profile.getBmi() != null ? profile.getBmi() : "Chưa cập nhật",
                    profile.getDailyCalorieTarget() != null ? profile.getDailyCalorieTarget() : "Chưa cập nhật"
                );
                if (profile.getAllergies() != null && !profile.getAllergies().isBlank()) {
                    for (String allergy : profile.getAllergies().split("[,;/]")) {
                        String trimmed = allergyRules.normalize(allergy);
                        if (!trimmed.isEmpty()) allergyKeywords.add(trimmed);
                    }
                }
            }
        }

        // 3. Filter meals (Safe Meals)
        List<Meal> safeMeals = new ArrayList<>();
        StringBuilder mealCatalog = new StringBuilder();
        int mealIndex = 0;

        for (Meal meal : allMeals) {
            List<MealIngredient> ingredients = ingredientsByMeal.getOrDefault(meal.getId(), List.of());
            List<String> primaryNames = new ArrayList<>();
            List<String> allIngredientNamesLower = new ArrayList<>();

            for (MealIngredient mi : ingredients) {
                String name = mi.getGenericName() != null ? mi.getGenericName() : mi.getProduct().getName();
                allIngredientNamesLower.add(name.toLowerCase());
                if ("PRIMARY".equals(mi.getRole())) primaryNames.add(name);
            }

            boolean hasDeletedProduct = ingredients.stream()
                    .filter(mi -> "PRIMARY".equals(mi.getRole()))
                    .anyMatch(mi -> {
                        String status = mi.getProduct().getStatus();
                        return "DELETED".equalsIgnoreCase(status) || "HIDDEN".equalsIgnoreCase(status);
                    });
            if (hasDeletedProduct) continue;

            boolean hasPrimaryOOS = primaryNames.stream()
                    .anyMatch(name -> oosLower.contains(name.toLowerCase()));
            if (hasPrimaryOOS) continue;

            if (!allergyKeywords.isEmpty()) {
                boolean hasAllergen = allIngredientNamesLower.stream()
                        .anyMatch(ing -> allergyRules.matchesAny(ing, allergyKeywords));
                if (hasAllergen) continue;
            }

            safeMeals.add(meal);
            mealIndex++;
            mealCatalog.append(mealIndex).append(". ").append(meal.getName());
            if (meal.getCategory() != null) mealCatalog.append(" [").append(meal.getCategory()).append("]");
            if (!primaryNames.isEmpty()) {
                mealCatalog.append(" | ").append(String.join(", ", primaryNames));
            }
            mealCatalog.append("\n");
        }

        // 4. Build Discount Catalog String
        StringBuilder discountCatalog = new StringBuilder();
        if (discountedVariants.isEmpty()) {
            discountCatalog.append("Hiện tại không có sản phẩm nào giảm giá.\n");
        } else {
            for (ProductVariant v : discountedVariants) {
                discountCatalog.append("- ").append(v.getProduct().getName());
                if (v.getVariantName() != null && !v.getVariantName().equals("Default")) {
                    discountCatalog.append(" (").append(v.getVariantName()).append(")");
                }
                discountCatalog.append("\n");
            }
        }

        String oosStr = oosProducts.isEmpty() ? "Không có" : String.join(", ", oosProducts);

        // 5. Save user message & context (create session if needed)
        User currentUser = loggedInUser;
        if (currentUser == null && requestDto.getUserId() != null) {
            currentUser = userRepository.findById(requestDto.getUserId()).orElse(null);
        }

        final ChatSession finalSession;
        if (currentUser != null) {
            ChatSession session = null;
            if (requestDto.getSessionId() != null) {
                session = chatHistoryService.getSessionDetails(requestDto.getSessionId(), currentUser).orElse(null);
            }
            if (session == null) {
                String latestMsg = extractLatestUserMessage(requestDto.getMessages());
                String sessionTitle = (latestMsg != null && !latestMsg.trim().isEmpty())
                        ? (latestMsg.length() > 40 ? latestMsg.substring(0, 37) + "..." : latestMsg.trim())
                        : "Cuộc trò chuyện mới";
                session = chatHistoryService.createSession(currentUser, sessionTitle, "GENERIC");
            }
            finalSession = session;
            String latestMsg = extractLatestUserMessage(requestDto.getMessages());
            if (latestMsg != null) {
                chatHistoryService.saveMessage(finalSession.getId(), "USER", latestMsg, null, null);
            }
        } else {
            finalSession = null;
        }

        // 6. Detect selection & intents locally via NLU Intent Router
        List<ChatResponseDto.ShoppingItem> shoppingItems = null;
        String directReply = null;
        String userLatestMsg = extractLatestUserMessage(requestDto.getMessages());

        if (userLatestMsg != null) {
            String sessionId = finalSession != null ? finalSession.getId().toString() : "session-temp";
            IntentResolverService.IntentResult resolved = intentResolverService.resolveIntent(userLatestMsg, sessionId, requestDto.getUserId());

            // Save conversation state for continuation
            String stateStr = null;
            if (resolved.intent() == IntentResolverService.IntentResult.IntentType.SHOPPING_SCENARIO) {
                stateStr = "SCENARIO:" + resolved.entity();
            } else if (resolved.intent() == IntentResolverService.IntentResult.IntentType.MEAL_RECIPE) {
                stateStr = "MEAL:" + resolved.entity();
            }

            if (stateStr != null) {
                stateManager.saveState(sessionId, resolved.intent().name(), resolved.entity());
                if (finalSession != null) {
                    chatHistoryService.updateSessionContextType(finalSession.getId(), stateStr);
                }
            }

            switch (resolved.intent()) {
                case MEAL_SELECTION: {
                    MealIntentService.MealSelectionResult selResult = mealIntentService.detectAndBuildShoppingSelection(
                            userLatestMsg, requestDto.getMessages(), safeMeals, ingredientsByMeal
                    );
                    shoppingItems = selResult.shoppingItems();
                    directReply = selResult.reply();
                    break;
                }
                case MEAL_RECIPE: {
                    String recipeQuery = (resolved.entity() != null && !resolved.entity().isEmpty()) ? resolved.entity() : userLatestMsg;
                    Meal fuzzyMatch = mealIntentService.findMealByFuzzyName(safeMeals, recipeQuery);
                    if (fuzzyMatch != null) {
                        shoppingItems = shoppingItemBuilder.buildShoppingItemsForMeal(fuzzyMatch, ingredientsByMeal);
                        directReply = String.format("Mình đã chuẩn bị sẵn đầy đủ nguyên liệu nấu \"%s\" bên dưới rồi nhé.", fuzzyMatch.getName());
                    }
                    break;
                }
                case BUY_PRODUCT: {
                    String query = resolved.entity() != null ? resolved.entity() : userLatestMsg;
                    List<ProductVariant> variants = productVariantRepository.findTop10ActiveByKeyword(query);
                    if (variants.isEmpty()) {
                        variants = productVariantRepository.searchActiveForSubstitution(query);
                    }
                    if (!variants.isEmpty()) {
                        shoppingItems = shoppingItemBuilder.buildShoppingItemsFromVariants(variants);
                        directReply = String.format("Mình đã tìm thấy sản phẩm phù hợp với \"%s\" trong cửa hàng. Bạn xem bên dưới nhé.", query);
                    } else {
                        directReply = String.format("Xin lỗi, hiện tại cửa hàng không có sản phẩm phù hợp với \"%s\" mà bạn yêu cầu.", query);
                    }
                    break;
                }
                case CHECK_DISCOUNT: {
                    DiscountIntentService.DiscountIntentResult discountResult = discountIntentService.detectDiscountIntent(
                            userLatestMsg, requestDto.getUserId()
                    );
                    shoppingItems = discountResult.shoppingItems();
                    directReply = discountResult.reply();
                    break;
                }
                case SHOPPING_SCENARIO: {
                    GeneralShoppingIntentService.GeneralShoppingResult scenarioResult = generalShoppingIntentService.detectGeneralShoppingIntent(userLatestMsg);
                    shoppingItems = scenarioResult.shoppingItems();
                    directReply = scenarioResult.reply();
                    break;
                }
                case CONTEXT_CONTINUATION:
                case GENERAL_CHAT:
                default: {
                    // Let OpenRouter handle generic chat
                    break;
                }
            }
        }

        // 7. Build System Prompt
        String systemPrompt = promptBuilderService.buildSystemPrompt(
                userLatestMsg,
                mealCatalog.toString(),
                discountCatalog.toString(),
                oosStr,
                userProfileStr,
                requestDto.getSystemPrompt()
        );

        // 8. Process Completion / Return Response
        ChatResponseDto response;
        if (directReply != null) {
            response = ChatResponseDto.builder()
                    .reply(directReply)
                    .success(true)
                    .shoppingItems(shoppingItems)
                    .build();
        } else {
            final List<ChatResponseDto.ShoppingItem> finalShoppingItems = shoppingItems;
            response = openRouterClient.chatCompletion(systemPrompt, requestDto.getMessages(), null, Duration.ofSeconds(18))
                    .map(result -> {
                        String aiReply = result.getReply();
                        List<ChatResponseDto.ShoppingItem> items = finalShoppingItems;

                        // Parse AI Intent Tags if no items were set locally
                        if (items == null || items.isEmpty()) {
                            Matcher searchMatcher = SEARCH_TAG_PATTERN.matcher(aiReply);
                            if (searchMatcher.find()) {
                                String keyword = searchMatcher.group(1).trim();
                                log.info("[AiChat] Intent Detected: SEARCH for '{}'", keyword);
                                items = shoppingItemBuilder.buildShoppingItemsFromVariants(
                                        productVariantRepository.findTop10ActiveByKeyword(keyword)
                                );
                                aiReply = searchMatcher.replaceAll("").trim();
                            } else if (aiReply.contains(SHOW_DISCOUNTS_TAG)) {
                                log.info("[AiChat] Intent Detected: SHOW_DISCOUNTS");
                                items = shoppingItemBuilder.buildShoppingItemsFromVariants(discountedVariants);
                                aiReply = aiReply.replace(SHOW_DISCOUNTS_TAG, "").trim();
                            }
                        }

                        return ChatResponseDto.builder()
                                .reply(aiReply)
                                .success(result.isSuccess())
                                .shoppingItems(items)
                                .build();
                    })
                    .block();
        }

        if (response == null) {
            response = ChatResponseDto.builder()
                    .reply("Xin lỗi, hệ thống AI đang bận hoặc quá tải. Vui lòng thử lại sau.")
                    .success(false)
                    .build();
        }

        // 9. Save assistant message
        if (response != null && finalSession != null) {
            long latency = System.currentTimeMillis() - t0;
            chatHistoryService.saveMessage(finalSession.getId(), "ASSISTANT", response.getReply(), response.getShoppingItems(), latency);
            response.setSessionId(finalSession.getId());
        }

        log.info("[AiChat] Total request time: {}ms", System.currentTimeMillis() - t0);
        return response;
    }

    private String extractLatestUserMessage(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> msg = messages.get(i);
            if ("user".equals(msg.get("role"))) return msg.get("content");
        }
        return null;
    }
}
