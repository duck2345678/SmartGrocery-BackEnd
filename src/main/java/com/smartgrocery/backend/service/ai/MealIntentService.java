package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.dto.ChatResponseDto;
import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.MealIngredient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealIntentService {

    private final ShoppingItemBuilder shoppingItemBuilder;

    private static final Pattern MEAL_SELECTION_PATTERN = Pattern.compile(
            "(?:.*?(?:mon|món|so|số|chon|chọn|lấy|lay|cho\\s*mình|mình\\s*chọn|mình\\s*lấy|" +
            "cho\\s*tôi|tôi\\s*chọn|tôi\\s*lấy|chọn\\s*món|lấy\\s*món|món\\s*số)\\s*)?\\s*(\\d+)\\s*(?:nhé|nhe|đi|di|nha|ạ|a|nhá)?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");

    private static final Pattern[] MEAL_NAME_EXTRACT_PATTERNS = {
        Pattern.compile("NUM\\.\\s*\\*{1,2}([^*\\n]+?)\\*{1,2}"),
        Pattern.compile("NUM\\.\\s*([^:\\n\\[\\(\\-]+?)\\s*(?:[:\\[\\(\\-]|$)"),
        Pattern.compile("NUM\\.\\s*(.+?)\\s*(?:\\n|$)"),
    };

    private static final Set<String> MEAL_QUERY_STOPWORDS = Set.of(
            "toi", "minh", "ban", "em", "anh", "chi",
            "muon", "can", "an", "uong", "goi", "y", "goi y",
            "mon", "chon", "lay", "giup", "cho", "nhe", "nha", "a",
            "bua", "buoi", "sang", "trua", "nay"
    );

    public record MealSelectionResult(String reply, List<ChatResponseDto.ShoppingItem> shoppingItems, boolean handled) {
        public static MealSelectionResult ignored() {
            return new MealSelectionResult(null, null, false);
        }

        public static MealSelectionResult selected(String reply, List<ChatResponseDto.ShoppingItem> shoppingItems) {
            return new MealSelectionResult(reply, shoppingItems, true);
        }

        public static MealSelectionResult invalid(String reply) {
            return new MealSelectionResult(reply, null, true);
        }
    }

    public MealSelectionResult detectAndBuildShoppingSelection(
            String userMessage,
            List<Map<String, String>> messages,
            List<Meal> safeMeals,
            Map<Long, List<MealIngredient>> ingredientsByMeal
    ) {
        String trimmed = userMessage == null ? "" : userMessage.trim();
        List<Integer> selectedNumbers = extractSelectedNumbers(trimmed);

        if (!selectedNumbers.isEmpty() && isSelectionMessage(trimmed)) {
            String lastAiSuggestion = findLastNumberedAiResponse(messages);
            if (lastAiSuggestion == null) {
                return MealSelectionResult.invalid("Mình chưa thấy danh sách món gần nhất để đối chiếu. Bạn chờ mình gợi ý lại rồi chọn theo số nhé.");
            }

            int maxNumber = countNumberedMeals(lastAiSuggestion);
            if (maxNumber <= 0) {
                return MealSelectionResult.invalid("Mình chưa đọc rõ danh sách món vừa rồi. Bạn chọn lại theo số thứ tự giúp mình nhé.");
            }

            List<Integer> invalidNumbers = selectedNumbers.stream()
                    .filter(number -> number < 1 || number > maxNumber)
                    .distinct()
                    .toList();
            if (!invalidNumbers.isEmpty()) {
                return MealSelectionResult.invalid(String.format(
                        "Mình chỉ thấy danh sách từ 1 đến %d. Số %s chưa hợp lệ, bạn chọn lại trong khoảng này nhé.",
                        maxNumber,
                        invalidNumbers.stream().map(String::valueOf).collect(Collectors.joining(", "))
                ));
            }

            List<ChatResponseDto.ShoppingItem> combinedItems = new ArrayList<>();
            List<String> chosenMealNames = new ArrayList<>();
            Set<Long> addedMealIds = new LinkedHashSet<>();

            for (Integer selectedNumber : selectedNumbers) {
                String mealName = extractMealNameFromNumberedList(lastAiSuggestion, selectedNumber);
                if (mealName == null) {
                    return MealSelectionResult.invalid(String.format(
                            "Mình chưa đọc được món ở số %d. Bạn thử chọn lại đúng số thứ tự giúp mình nhé.",
                            selectedNumber
                    ));
                }

                Meal matchedMeal = findMealByFuzzyName(safeMeals, mealName);
                if (matchedMeal == null) {
                    return MealSelectionResult.invalid(String.format(
                            "Mình chưa ghép được món ở số %d với dữ liệu hiện có. Bạn thử chọn lại giúp mình nhé.",
                            selectedNumber
                    ));
                }

                if (addedMealIds.add(matchedMeal.getId())) {
                    chosenMealNames.add(matchedMeal.getName());
                    List<ChatResponseDto.ShoppingItem> mealItems = shoppingItemBuilder.buildShoppingItemsForMeal(matchedMeal, ingredientsByMeal);
                    if (mealItems != null) {
                        combinedItems.addAll(mealItems);
                    }
                }
            }

            if (combinedItems.isEmpty()) {
                return MealSelectionResult.invalid("Mình chưa tạo được danh sách nguyên liệu cho các món bạn chọn. Bạn thử lại giúp mình nhé.");
            }

            return MealSelectionResult.selected(
                    String.format(
                            "Mình đã chuẩn bị danh sách nguyên liệu cho %s rồi nhé. Bạn xem bên dưới và thêm vào giỏ hàng khi tiện nha.",
                            String.join(", ", chosenMealNames)
                    ),
                    deduplicateShoppingItems(combinedItems)
            );
        }

        if (selectedNumbers.isEmpty() && looksLikeInvalidSelectionText(trimmed)) {
            return MealSelectionResult.invalid("Mình chưa hiểu lựa chọn đó. Bạn hãy chọn lại bằng số thứ tự, ví dụ `1`, `2` hoặc `1 2 3` nhé.");
        }

        List<ChatResponseDto.ShoppingItem> fallbackItems = detectAndBuildShoppingList(trimmed, messages, safeMeals, ingredientsByMeal);
        if (fallbackItems != null && !fallbackItems.isEmpty()) {
            return MealSelectionResult.selected(
                    "Mình đã chuẩn bị danh sách nguyên liệu rồi nhé. Bạn xem bên dưới và thêm vào giỏ hàng khi tiện nha.",
                    fallbackItems
            );
        }

        return MealSelectionResult.ignored();
    }

    public List<ChatResponseDto.ShoppingItem> detectAndBuildShoppingList(
            String userMessage,
            List<Map<String, String>> messages,
            List<Meal> safeMeals,
            Map<Long, List<MealIngredient>> ingredientsByMeal
    ) {
        Matcher matcher = MEAL_SELECTION_PATTERN.matcher(userMessage.trim());
        if (matcher.matches()) {
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
            if (matchedMeal != null) return shoppingItemBuilder.buildShoppingItemsForMeal(matchedMeal, ingredientsByMeal);
        }

        String trimmed = userMessage.trim();
        if (trimmed.length() >= 4 && trimmed.length() <= 80) {
            Meal fuzzyMatch = findMealByFuzzyName(safeMeals, trimmed);
            if (fuzzyMatch != null) {
                log.info("[MealDetect] Fuzzy name fallback matched: '{}' → '{}'", trimmed, fuzzyMatch.getName());
                return shoppingItemBuilder.buildShoppingItemsForMeal(fuzzyMatch, ingredientsByMeal);
            }
        }

        return null;
    }

    public Meal findMealByFuzzyName(List<Meal> meals, String targetName) {
        String normTarget = normalizeMealSearchText(targetName);
        Set<String> targetTokens = tokenizeMealSearchText(normTarget);
        if (targetTokens.isEmpty()) {
            return null;
        }
        for (Meal meal : meals) {
            String normMeal = normalizeMealSearchText(meal.getName());
            if (normMeal.contains(normTarget) || normTarget.contains(normMeal)) return meal;
        }
        if (targetTokens.size() == 1) {
            String onlyToken = targetTokens.iterator().next();
            for (Meal meal : meals) {
                Set<String> mealTokens = tokenizeMealSearchText(normalizeMealSearchText(meal.getName()));
                if (mealTokens.contains(onlyToken)) {
                    return meal;
                }
            }
        }
        Meal bestMatch = null;
        double bestScore = 0.0;
        for (Meal meal : meals) {
            Set<String> mealTokens = tokenizeMealSearchText(normalizeMealSearchText(meal.getName()));
            long overlap = mealTokens.stream().filter(targetTokens::contains).count();
            double score = (double) overlap / Math.max(mealTokens.size(), 1);
            if (overlap >= 2 && score > bestScore) { bestScore = score; bestMatch = meal; }
        }
        return bestScore >= 0.4 ? bestMatch : null;
    }

    public Set<String> tokenizeMealSearchText(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(normalizedText.split("\\s+"))
                .filter(token -> !token.isBlank())
                .filter(token -> !MEAL_QUERY_STOPWORDS.contains(token))
                .collect(Collectors.toCollection(HashSet::new));
    }

    public String normalizeMealSearchText(String text) {
        return normalizeVietnamese(canonicalizeMealTerms(text));
    }

    public String canonicalizeMealTerms(String text) {
        if (text == null) return "";
        String value = text.toLowerCase(java.util.Locale.ROOT);
        value = value.replaceAll("(?iu)\\bphô\\s+mai\\b", " cheese ");
        value = value.replaceAll("(?iu)\\bpho\\s+mai\\b", " cheese ");
        value = value.replaceAll("(?iu)\\bphở\\b", " phonoodle ");
        value = value.replaceAll("(?iu)\\bpho\\b", " phonoodle ");
        return value;
    }

    public String normalizeVietnamese(String text) {
        if (text == null) return "";
        String decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd').replace('\u0110', 'D')
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<Integer> extractSelectedNumbers(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }

        List<Integer> numbers = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(userMessage);
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group()));
        }
        return numbers.stream().distinct().toList();
    }

    private boolean isSelectionMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        String normalized = normalizeVietnamese(userMessage)
                .replaceAll("\\b(va|chon|mon|so|lay|toi|minh|cho|giup|nhe|nha|di|dum|voi)\\b", " ")
                .replaceAll("[,.;:/\\\\()\\-+&]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isBlank()) {
            return false;
        }

        return normalized.matches("[0-9\\s]+");
    }

    private boolean looksLikeInvalidSelectionText(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        String normalized = normalizeVietnamese(userMessage).trim();
        if (normalized.length() > 40) {
            return false;
        }

        boolean containsSelectionWord = normalized.matches(".*\\b(chon|mon|so|lua chon)\\b.*");
        boolean noDigits = !NUMBER_PATTERN.matcher(userMessage).find();
        return containsSelectionWord && noDigits;
    }

    private int countNumberedMeals(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) {
            return 0;
        }

        int max = 0;
        Matcher matcher = Pattern.compile("(?m)^\\s*(\\d+)\\.").matcher(aiResponse);
        while (matcher.find()) {
            max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return max;
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

    private List<ChatResponseDto.ShoppingItem> deduplicateShoppingItems(List<ChatResponseDto.ShoppingItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Map<String, ChatResponseDto.ShoppingItem> deduped = new LinkedHashMap<>();
        for (ChatResponseDto.ShoppingItem item : items) {
            if (item == null) {
                continue;
            }
            String key = item.getProductId() + ":" + item.getVariantId();
            deduped.putIfAbsent(key, item);
        }
        return new ArrayList<>(deduped.values());
    }
}
