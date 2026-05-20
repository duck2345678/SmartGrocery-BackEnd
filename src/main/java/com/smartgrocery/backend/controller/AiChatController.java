package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.ChatRequestDto;
import com.smartgrocery.backend.dto.ChatResponseDto;
import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.UserNutritionProfile;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.MealIngredientRepository;
import com.smartgrocery.backend.repository.jpa.MealRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.UserNutritionProfileRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import com.smartgrocery.backend.service.ChatHistoryService;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.ChatSession;
import com.smartgrocery.backend.service.ai.OpenRouterClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "AI Chat", description = "API nền tảng hội thoại với AI")
public class AiChatController {

    @Autowired private OpenRouterClient openRouterClient;
    @Autowired private MealRepository mealRepository;
    @Autowired private MealIngredientRepository mealIngredientRepository;
    @Autowired private UserNutritionProfileRepository nutritionProfileRepository;
    @Autowired private InventoryStockRepository inventoryStockRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatHistoryService chatHistoryService;

    // ── In-memory TTL cache for meal & discount catalog (avoid reloading on every request) ──
    private volatile List<Meal> cachedMeals = null;
    private volatile Map<Long, List<MealIngredient>> cachedIngredientsByMeal = null;
    private volatile List<ProductVariant> cachedDiscountedVariants = null;
    private volatile long catalogCacheExpiry = 0L;
    private static final long CATALOG_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    // Broad pattern to detect meal selection by number
    private static final Pattern MEAL_SELECTION_PATTERN = Pattern.compile(
            "(?:.*?(?:mon|món|so|số|chon|chọn|lấy|lay|cho\\s*mình|mình\\s*chọn|mình\\s*lấy|" +
            "cho\\s*tôi|tôi\\s*chọn|tôi\\s*lấy|chọn\\s*món|lấy\\s*món|món\\s*số)\\s*)?\\s*(\\d+)\\s*(?:nhé|nhe|đi|di|nha|ạ|a|nhá)?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // Multiple patterns for extracting meal name from AI numbered response
    private static final Pattern[] MEAL_NAME_EXTRACT_PATTERNS = {
        Pattern.compile("NUM\\.\\s*\\*{1,2}([^*\\n]+?)\\*{1,2}"),
        Pattern.compile("NUM\\.\\s*([^:\\n\\[\\(\\-]+?)\\s*(?:[:\\[\\(\\-]|$)"),
        Pattern.compile("NUM\\.\\s*(.+?)\\s*(?:\\n|$)"),
    };

    // AI Intent Tags
    private static final Pattern SEARCH_TAG_PATTERN = Pattern.compile("\\[SEARCH:\\s*([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
    private static final String SHOW_DISCOUNTS_TAG = "[SHOW_DISCOUNTS]";

    /**
     * Returns cached catalogs.
     * Cache is valid for 5 minutes. Uses double-checked locking for thread safety.
     */
    private void ensureCatalogCached() {
        if (System.currentTimeMillis() < catalogCacheExpiry && cachedMeals != null) {
            return; // Cache is still valid
        }
        synchronized (this) {
            if (System.currentTimeMillis() < catalogCacheExpiry && cachedMeals != null) {
                return; // Double-check after acquiring lock
            }
            long t0 = System.currentTimeMillis();

            // 1. Batch query: load ALL meals
            List<Meal> meals = mealRepository.findAll();

            // 2. Batch query: load ALL meal ingredients
            List<MealIngredient> allIngredients = mealIngredientRepository.findAllWithProduct();
            Map<Long, List<MealIngredient>> byMeal = new HashMap<>();
            for (MealIngredient mi : allIngredients) {
                byMeal.computeIfAbsent(mi.getMeal().getId(), k -> new ArrayList<>()).add(mi);
            }

            // 3. Batch query: load Top 10 discounts
            List<ProductVariant> discounts = productVariantRepository.findTop10DiscountedVariants();

            cachedMeals = meals;
            cachedIngredientsByMeal = byMeal;
            cachedDiscountedVariants = discounts;
            catalogCacheExpiry = System.currentTimeMillis() + CATALOG_CACHE_TTL_MS;
            log.info("[CatalogCache] Rebuilt: {} meals, {} ingredient rows, {} discounts in {}ms",
                    meals.size(), allIngredients.size(), discounts.size(), System.currentTimeMillis() - t0);
        }
    }

    @Operation(summary = "Gửi tin nhắn hội thoại đến AI")
    @PostMapping
    public ResponseEntity<ChatResponseDto> sendMessage(
            @AuthenticationPrincipal User loggedInUser,
            @RequestBody ChatRequestDto requestDto
    ) {
        long t0 = System.currentTimeMillis();

        ensureCatalogCached();
        List<Meal> allMeals = cachedMeals;
        Map<Long, List<MealIngredient>> ingredientsByMeal = cachedIngredientsByMeal;
        List<ProductVariant> discountedVariants = cachedDiscountedVariants;

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
                        String trimmed = allergy.trim().toLowerCase();
                        if (!trimmed.isEmpty()) allergyKeywords.add(trimmed);
                    }
                }
            }
        }

        // ── HARD FILTER Meals ──
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
                        .anyMatch(ing -> allergyKeywords.stream().anyMatch(a -> ing.contains(a) || a.contains(ing)));
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

        // ── BUILD Discount Catalog ──
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

        // ── DETECT Meal Selection from History ──
        List<ChatResponseDto.ShoppingItem> shoppingItems = null;
        String userLatestMsg = extractLatestUserMessage(requestDto.getMessages());
        if (userLatestMsg != null) {
            shoppingItems = detectAndBuildShoppingList(userLatestMsg, requestDto.getMessages(), safeMeals, ingredientsByMeal);
        }

        // ── 6. Construct System Prompt ──
        String systemPrompt = requestDto.getSystemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "Bạn là trợ lý mua sắm thông minh của SmartGrocery. Trả lời ngắn gọn, tự nhiên bằng tiếng Việt.\n\n"
                    + "=== HƯỚNG DẪN XỬ LÝ YÊU CẦU ===\n\n"
                    + "[A] GỢI Ý MÓN ĂN:\n"
                    + "- Dùng DANH SÁCH MÓN bên dưới, đánh số 1. 2. 3.\n"
                    + "- Khi khách chọn số: Xác nhận tên món, nói 'Đã chuẩn bị nguyên liệu cho [Tên] rồi nhé!'. KHÔNG liệt kê nguyên liệu.\n\n"
                    + "[B] TÌM SẢN PHẨM hoặc GỢI Ý ẨN Ý:\n"
                    + "- Khi khách hỏi mua sản phẩm cụ thể (tương ớt, sữa, nước rửa chén...) HOẶC nói ẩn ý cần mua đồ:\n"
                    + "  * 'nhà bẩn quá' → nước lau sàn\n"
                    + "  * 'khát nước' → nước suối, nước giải khát\n"
                    + "  * 'cần bột giặt' → bột giặt\n"
                    + "- Trả lời tự nhiên VÀ BẮT BUỘC thêm tag [SEARCH: <từ_khóa>] Ở CUỐI CÂU (không có khoảng trắng thừa).\n"
                    + "- Ví dụ đúng: 'Bạn có thể dùng nước lau sàn Sunlight để vệ sinh sạch sẽ! [SEARCH: nước lau sàn]'\n\n"
                    + "[C] CÂU HỎI GIẢM GIÁ:\n"
                    + "- Hỏi chung ('có gì giảm', 'sale gì hôm nay'): Trả lời ngắn VÀ thêm [SHOW_DISCOUNTS] ở cuối.\n"
                    + "- Hỏi sản phẩm CỤ THỂ có giảm không (vd: 'trứng có giảm giá không'):  Tra DANH SÁCH GIẢM GIÁ.\n"
                    + "  * Nếu CÓ → trả lời + [SEARCH: <tên sản phẩm đó>]\n"
                    + "  * Nếu KHÔNG → báo không có, hỏi có muốn xem list giảm giá khác không. Nếu khách đồng ý → [SHOW_DISCOUNTS]\n\n"
                    + "[D] CÂU HỎI CHUNG (không liên quan mua sắm):\n"
                    + "- Trả lời bình thường, tự nhiên, không thêm tag gì cả.\n"
                    + "- Sau đó có thể nhẹ nhàng hướng về mua sắm nếu phù hợp.\n\n"
                    + "=== DỮ LIỆU ===\n"
                    + "DANH SÁCH MÓN:\n" + mealCatalog + "\n"
                    + "GIẢM GIÁ HÔM NAY (top 10):\n" + discountCatalog + "\n"
                    + "HẾT HÀNG: " + oosStr + "\n"
                    + (userProfileStr.isBlank() ? "" : "\nHỒ SƠ KHÁCH HÀNG:" + userProfileStr);
        }

        final List<ChatResponseDto.ShoppingItem> finalShoppingItems = shoppingItems;

        // ── SAVE USER MESSAGE & CONTEXT ──
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

        // ── CALL AI ──
        ChatResponseDto response = openRouterClient.chatCompletion(systemPrompt, requestDto.getMessages())
                .map(result -> {
                    String aiReply = result.getReply();
                    List<ChatResponseDto.ShoppingItem> items = finalShoppingItems;

                    // Parse AI Intent Tags if no meal was selected
                    if (items == null || items.isEmpty()) {
                        Matcher searchMatcher = SEARCH_TAG_PATTERN.matcher(aiReply);
                        if (searchMatcher.find()) {
                            String keyword = searchMatcher.group(1).trim();
                            log.info("[AiChat] Intent Detected: SEARCH for '{}'", keyword);
                            items = buildShoppingItemsFromVariants(
                                    productVariantRepository.findTop10ActiveByKeyword(keyword)
                            );
                            aiReply = searchMatcher.replaceAll("").trim(); // Remove tag
                        } else if (aiReply.contains(SHOW_DISCOUNTS_TAG)) {
                            log.info("[AiChat] Intent Detected: SHOW_DISCOUNTS");
                            items = buildShoppingItemsFromVariants(discountedVariants);
                            aiReply = aiReply.replace(SHOW_DISCOUNTS_TAG, "").trim(); // Remove tag
                        }
                    }

                    return ChatResponseDto.builder()
                            .reply(aiReply)
                            .success(result.isSuccess())
                            .shoppingItems(items)
                            .build();
                })
                .block();

        // ── SAVE ASSISTANT REPLY ──
        if (response != null && finalSession != null) {
            long latency = System.currentTimeMillis() - t0;
            chatHistoryService.saveMessage(finalSession.getId(), "ASSISTANT", response.getReply(), response.getShoppingItems(), latency);
            response.setSessionId(finalSession.getId());
        }

        log.info("[AiChat] Total request time: {}ms", System.currentTimeMillis() - t0);
        return ResponseEntity.ok(response);
    }

    private String extractLatestUserMessage(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> msg = messages.get(i);
            if ("user".equals(msg.get("role"))) return msg.get("content");
        }
        return null;
    }

    private List<ChatResponseDto.ShoppingItem> detectAndBuildShoppingList(
            String userMessage,
            List<Map<String, String>> messages,
            List<Meal> safeMeals,
            Map<Long, List<MealIngredient>> ingredientsByMeal
    ) {
        // ONLY trigger meal ingredient list via number selection.
        // Direct fuzzy name matching is removed to prevent false positives
        // (e.g. "nước mắm" matching a meal that contains nước mắm as ingredient).
        Matcher matcher = MEAL_SELECTION_PATTERN.matcher(userMessage.trim());
        if (!matcher.matches()) return null;

        int selectedNumber = Integer.parseInt(matcher.group(1));
        log.info("[MealDetect] Number pattern matched → #{}", selectedNumber);
        if (selectedNumber < 1) return null;

        String lastAiSuggestion = findLastNumberedAiResponse(messages);
        if (lastAiSuggestion == null) {
            log.warn("[MealDetect] No numbered AI response in history");
            return null;
        }
        String mealName = extractMealNameFromNumberedList(lastAiSuggestion, selectedNumber);
        if (mealName == null) {
            log.warn("[MealDetect] Could not extract name for #{}", selectedNumber);
            return null;
        }
        log.info("[MealDetect] Extracted name: '{}'", mealName);
        Meal matchedMeal = findMealByFuzzyName(safeMeals, mealName);
        if (matchedMeal == null) return null;
        return buildShoppingItemsForMeal(matchedMeal, ingredientsByMeal);
    }

    private String findLastNumberedAiResponse(List<Map<String, String>> messages) {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> msg = messages.get(i);
            if ("assistant".equals(msg.get("role"))) {
                String content = msg.get("content");
                if (content != null && content.contains("1.") && content.contains("2.")) return content;
            }
        }
        return null;
    }

    private String extractMealNameFromNumberedList(String aiResponse, int number) {
        for (Pattern template : MEAL_NAME_EXTRACT_PATTERNS) {
            Pattern pattern = Pattern.compile(template.pattern().replace("NUM", String.valueOf(number)));
            Matcher m = pattern.matcher(aiResponse);
            if (m.find()) {
                String name = m.group(1).trim().replaceAll("\\*+", "").trim();
                if (!name.isBlank() && name.length() >= 2) return name;
            }
        }
        return null;
    }

    private List<ChatResponseDto.ShoppingItem> buildShoppingItemsForMeal(
            Meal meal,
            Map<Long, List<MealIngredient>> ingredientsByMeal
    ) {
        List<MealIngredient> ingredients = ingredientsByMeal.getOrDefault(meal.getId(), List.of());
        if (ingredients.isEmpty()) return null;

        List<Long> productIds = ingredients.stream()
                .map(mi -> mi.getProduct().getId())
                .distinct()
                .collect(Collectors.toList());
        List<ProductVariant> variants = productVariantRepository.findByProduct_IdInAndStatus(productIds, "ACTIVE");
        Map<Long, ProductVariant> cheapestVariantByProduct = new HashMap<>();
        for (ProductVariant v : variants) {
            Long pid = v.getProduct().getId();
            if (!cheapestVariantByProduct.containsKey(pid) ||
                v.getNetPrice().compareTo(cheapestVariantByProduct.get(pid).getNetPrice()) < 0) {
                cheapestVariantByProduct.put(pid, v);
            }
        }

        List<ChatResponseDto.ShoppingItem> items = new ArrayList<>();
        for (MealIngredient mi : ingredients) {
            Product product = mi.getProduct();
            ProductVariant variant = cheapestVariantByProduct.get(product.getId());
            items.add(ChatResponseDto.ShoppingItem.builder()
                    .productId(product.getId())
                    .variantId(variant != null ? variant.getId() : null)
                    .name(product.getName())
                    .imageUrl(product.getImage())
                    .price(variant != null ? variant.getNetPrice() : null)
                    .unit(variant != null ? variant.getUnit() : "")
                    .role(mi.getRole())
                    .build());
        }
        log.info("[MealDetect] Built {} items for '{}'", items.size(), meal.getName());
        return items;
    }

    private List<ChatResponseDto.ShoppingItem> buildShoppingItemsFromVariants(List<ProductVariant> variants) {
        if (variants == null || variants.isEmpty()) return null;
        return variants.stream().map(v -> ChatResponseDto.ShoppingItem.builder()
                .productId(v.getProduct().getId())
                .variantId(v.getId())
                .name(v.getProduct().getName())
                .imageUrl(v.getProduct().getImage())
                .price(v.getNetPrice())
                .unit(v.getUnit())
                .role("PRODUCT") // Distinct from meal ingredient roles (PRIMARY/SECONDARY)
                .build()).collect(Collectors.toList());
    }

    private Meal findMealByFuzzyName(List<Meal> meals, String targetName) {
        String normTarget = normalizeVietnamese(targetName);
        for (Meal meal : meals) {
            String normMeal = normalizeVietnamese(meal.getName());
            if (normMeal.contains(normTarget) || normTarget.contains(normMeal)) return meal;
        }
        Set<String> targetTokens = new HashSet<>(Arrays.asList(normTarget.split("\\s+")));
        Meal bestMatch = null;
        double bestScore = 0.0;
        for (Meal meal : meals) {
            Set<String> mealTokens = new HashSet<>(Arrays.asList(normalizeVietnamese(meal.getName()).split("\\s+")));
            long overlap = mealTokens.stream().filter(targetTokens::contains).count();
            double score = (double) overlap / Math.max(mealTokens.size(), 1);
            if (overlap >= 2 && score > bestScore) { bestScore = score; bestMatch = meal; }
        }
        return bestScore >= 0.4 ? bestMatch : null;
    }

    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        String decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd').replace('\u0110', 'D')
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
