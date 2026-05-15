package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealCatalogService {

    private final ObjectMapper objectMapper;

    @Value("classpath:data/international_food_dataset_1000_vi.json")
    private Resource catalogResource;

    private volatile List<MealCatalogItem> cachedItems;

    public record CatalogMealOption(
            int optionNo,
            String title,
            List<String> ingredients,
            String reason
    ) {}

    public List<CatalogMealOption> suggestMealOptions(String userMessage, int variant, Set<String> avoidanceTokens, int limit) {
        List<MealCatalogItem> items = loadCatalog();
        if (items.isEmpty() || limit <= 0) {
            return List.of();
        }

        String normalizedMessage = normalizeText(userMessage);
        List<ScoredMeal> scored = items.stream()
                .filter(item -> isMealTypeCompatible(item, normalizedMessage))
                .filter(item -> !violatesAvoidance(item, avoidanceTokens))
                .map(item -> new ScoredMeal(item, scoreMeal(item, normalizedMessage)))
                .filter(scoredMeal -> scoredMeal.score() > 0)
                .sorted(Comparator.comparingInt(ScoredMeal::score).reversed()
                        .thenComparing(scoredMeal -> scoredMeal.item().getId()))
                .toList();

        if (scored.isEmpty()) {
            return List.of();
        }

        int start = Math.floorMod(variant, Math.max(1, Math.min(8, scored.size())));
        List<ScoredMeal> rotated = new ArrayList<>(scored.size());
        rotated.addAll(scored.subList(start, scored.size()));
        rotated.addAll(scored.subList(0, start));

        List<CatalogMealOption> options = new ArrayList<>();
        LinkedHashSet<String> usedNames = new LinkedHashSet<>();
        for (ScoredMeal scoredMeal : rotated) {
            MealCatalogItem item = scoredMeal.item();
            if (item.getName() == null || item.getName().isBlank() || !usedNames.add(normalizeText(item.getName()))) {
                continue;
            }
            List<String> ingredients = item.getMainIngredients() == null
                    ? List.of()
                    : item.getMainIngredients().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .limit(5)
                    .toList();
            if (ingredients.size() < 2) {
                continue;
            }
            options.add(new CatalogMealOption(
                    options.size() + 1,
                    item.getName(),
                    ingredients,
                    buildReason(item)
            ));
            if (options.size() >= limit) {
                break;
            }
        }
        return options;
    }

    private List<MealCatalogItem> loadCatalog() {
        List<MealCatalogItem> local = cachedItems;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedItems != null) {
                return cachedItems;
            }
            try (InputStreamReader reader = new InputStreamReader(
                    catalogResource.getInputStream(),
                    java.nio.charset.StandardCharsets.UTF_8
            )) {
                cachedItems = objectMapper.readValue(reader, new TypeReference<>() {});
                log.info("Loaded {} meal catalog items", cachedItems.size());
                return cachedItems;
            } catch (Exception e) {
                log.warn("Could not load meal catalog: {}", e.getMessage());
                cachedItems = List.of();
                return cachedItems;
            }
        }
    }

    private boolean isMealTypeCompatible(MealCatalogItem item, String normalizedMessage) {
        Set<String> mealTypes = normalizeList(item.getMealType());
        if (normalizedMessage.contains("bua sang") || normalizedMessage.contains("an sang")) {
            return mealTypes.contains("breakfast");
        }
        if (normalizedMessage.contains("bua trua") || normalizedMessage.contains("an trua")) {
            return mealTypes.contains("lunch");
        }
        if (normalizedMessage.contains("bua toi") || normalizedMessage.contains("an toi")) {
            return mealTypes.contains("dinner");
        }
        return true;
    }

    private boolean violatesAvoidance(MealCatalogItem item, Set<String> avoidanceTokens) {
        if (avoidanceTokens == null || avoidanceTokens.isEmpty()) {
            return false;
        }
        String haystack = normalizeText(String.join(" ",
                item.getName() != null ? item.getName() : "",
                join(item.getMainIngredients()),
                join(item.getOptionalIngredients()),
                join(item.getAvoidIfAllergicTo()),
                join(item.getShoppingKeywords())
        ));
        return avoidanceTokens.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeText)
                .filter(term -> !term.isBlank())
                .anyMatch(term -> containsNormalizedPhrase(haystack, term) || haystack.contains(term));
    }

    private int scoreMeal(MealCatalogItem item, String normalizedMessage) {
        int score = 10;
        Set<String> mealTypes = normalizeList(item.getMealType());
        Set<String> categories = normalizeList(item.getCategory());
        Set<String> dietTags = normalizeList(item.getDietTags());
        String haystack = normalizeText(String.join(" ",
                item.getName() != null ? item.getName() : "",
                item.getCountry() != null ? item.getCountry() : "",
                item.getCuisineRegion() != null ? item.getCuisineRegion() : "",
                join(item.getMainIngredients()),
                join(item.getFlavorProfile()),
                join(item.getShoppingKeywords())
        ));

        if ((normalizedMessage.contains("bua toi") || normalizedMessage.contains("an toi")) && mealTypes.contains("dinner")) score += 40;
        if ((normalizedMessage.contains("bua sang") || normalizedMessage.contains("an sang")) && mealTypes.contains("breakfast")) score += 40;
        if ((normalizedMessage.contains("bua trua") || normalizedMessage.contains("an trua")) && mealTypes.contains("lunch")) score += 40;
        if (normalizedMessage.contains("healthy") || normalizedMessage.contains("giam can") || normalizedMessage.contains("it calo")) {
            if (categories.contains("healthy") || dietTags.contains("low calorie") || dietTags.contains("balanced")) score += 30;
        }
        if (normalizedMessage.contains("an chay")) {
            if (categories.contains("vegetarian") || dietTags.contains("vegetarian") || dietTags.contains("vegan")) score += 45;
            else score -= 20;
        }
        if (normalizedMessage.contains("protein") || normalizedMessage.contains("tang co")) {
            if (dietTags.contains("high protein") || containsAny(haystack, List.of("ga", "bo", "trung", "dau hu", "ca"))) score += 25;
        }
        if (normalizedMessage.contains("ca phe") && (categories.contains("snack") || categories.contains("drinking food"))) {
            score += 20;
        }
        if ("easy".equalsIgnoreCase(item.getDifficulty())) score += 5;
        if (item.getCookingTimeMinutes() != null && item.getCookingTimeMinutes() <= 45) score += 5;
        if (categories.contains("simple home cooking")) score += 8;
        if (categories.contains("family meal")) score += 5;

        return score;
    }

    private String buildReason(MealCatalogItem item) {
        List<String> parts = new ArrayList<>();
        if (item.getDietTags() != null && !item.getDietTags().isEmpty()) {
            parts.add(String.join(", ", item.getDietTags()));
        }
        if (item.getCookingTimeMinutes() != null) {
            parts.add("khoảng " + item.getCookingTimeMinutes() + " phút");
        }
        if (item.getCountry() != null && !item.getCountry().isBlank()) {
            parts.add("phong cách " + item.getCountry());
        }
        return parts.isEmpty()
                ? "phù hợp với nhu cầu bữa ăn hiện tại"
                : String.join(", ", parts);
    }

    private Set<String> normalizeList(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeText)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    private boolean containsAny(String haystack, List<String> needles) {
        return needles.stream().anyMatch(needle -> containsNormalizedPhrase(haystack, needle));
    }

    private boolean containsNormalizedPhrase(String normalizedText, String phrase) {
        String normalizedPhrase = normalizeText(phrase);
        if (normalizedText.isBlank() || normalizedPhrase.isBlank()) {
            return false;
        }
        return (" " + normalizedText + " ").contains(" " + normalizedPhrase + " ");
    }

    private String join(List<String> values) {
        return values == null ? "" : String.join(" ", values);
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record ScoredMeal(MealCatalogItem item, int score) {}

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MealCatalogItem {
        private Integer id;
        private String name;
        private String country;
        @JsonProperty("cuisine_region")
        private String cuisineRegion;
        @JsonProperty("meal_type")
        private List<String> mealType;
        private List<String> category;
        @JsonProperty("main_ingredients")
        private List<String> mainIngredients;
        @JsonProperty("optional_ingredients")
        private List<String> optionalIngredients;
        @JsonProperty("avoid_if_allergic_to")
        private List<String> avoidIfAllergicTo;
        @JsonProperty("diet_tags")
        private List<String> dietTags;
        private String difficulty;
        @JsonProperty("cooking_time_minutes")
        private Integer cookingTimeMinutes;
        @JsonProperty("flavor_profile")
        private List<String> flavorProfile;
        @JsonProperty("shopping_keywords")
        private List<String> shoppingKeywords;
    }
}
