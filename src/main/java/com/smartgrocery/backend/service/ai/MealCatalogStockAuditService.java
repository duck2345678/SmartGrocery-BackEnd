package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealCatalogStockAuditService {

    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryStockRepository inventoryStockRepository;

    @Value("classpath:data/international_food_dataset_1000_vi.json")
    private Resource catalogResource;

    public MealCatalogAuditReport auditCatalogAgainstCurrentStock() {
        List<AuditMealItem> meals = loadMeals();
        List<Product> activeProducts = productRepository.findActiveWithCategory();
        List<Long> activeProductIds = activeProducts.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .toList();

        List<ProductVariant> activeVariants = activeProductIds.isEmpty()
                ? List.of()
                : productVariantRepository.findByProductIdsAndStatusWithProduct(activeProductIds, "ACTIVE");
        List<Long> activeVariantIds = activeVariants.stream()
                .map(ProductVariant::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Long> stockByVariantId = activeVariantIds.isEmpty()
                ? Map.of()
                : inventoryStockRepository.sumAvailableByVariantIds(activeVariantIds).stream()
                .collect(Collectors.toMap(
                        InventoryStockRepository.VariantStockSum::getVariantId,
                        stock -> stock.getTotalAvailable() == null ? 0L : stock.getTotalAvailable()
                ));

        Map<Long, Long> stockByProductId = activeVariants.stream()
                .filter(variant -> variant.getProduct() != null && variant.getProduct().getId() != null)
                .collect(Collectors.groupingBy(
                        variant -> variant.getProduct().getId(),
                        Collectors.summingLong(variant -> stockByVariantId.getOrDefault(variant.getId(), 0L))
                ));

        List<ProductCandidate> candidates = activeProducts.stream()
                .map(product -> toCandidate(product, stockByProductId.getOrDefault(product.getId(), 0L)))
                .toList();

        List<MealAuditDetail> details = new ArrayList<>();
        Map<String, Integer> missingIngredientCounts = new HashMap<>();
        Map<String, Integer> outOfStockIngredientCounts = new HashMap<>();
        int cookable = 0;
        int partial = 0;
        int notCookable = 0;

        for (AuditMealItem meal : meals) {
            List<String> ingredients = normalizedIngredientList(meal.getMainIngredients());
            List<IngredientAudit> ingredientAudits = ingredients.stream()
                    .map(ingredient -> auditIngredient(ingredient, candidates))
                    .toList();
            long inStockMatches = ingredientAudits.stream()
                    .filter(audit -> "IN_STOCK".equals(audit.status()))
                    .count();
            long matchedButOutOfStock = ingredientAudits.stream()
                    .filter(audit -> "OUT_OF_STOCK".equals(audit.status()))
                    .count();
            long missing = ingredientAudits.stream()
                    .filter(audit -> "MISSING_PRODUCT".equals(audit.status()))
                    .count();

            ingredientAudits.forEach(audit -> {
                if ("MISSING_PRODUCT".equals(audit.status())) {
                    missingIngredientCounts.merge(audit.ingredient(), 1, Integer::sum);
                } else if ("OUT_OF_STOCK".equals(audit.status())) {
                    outOfStockIngredientCounts.merge(audit.ingredient(), 1, Integer::sum);
                }
            });

            String status;
            double coverage = ingredients.isEmpty() ? 0.0 : (double) inStockMatches / ingredients.size();
            if (!ingredients.isEmpty() && inStockMatches == ingredients.size()) {
                status = "COOKABLE";
                cookable++;
            } else if (coverage >= 0.40) {
                status = "PARTIAL";
                partial++;
            } else {
                status = "NOT_COOKABLE";
                notCookable++;
            }

            details.add(new MealAuditDetail(
                    meal.getId(),
                    meal.getName(),
                    meal.getMealType() == null ? List.of() : meal.getMealType(),
                    ingredients,
                    status,
                    round(coverage),
                    (int) inStockMatches,
                    (int) matchedButOutOfStock,
                    (int) missing,
                    ingredientAudits
            ));
        }

        return new MealCatalogAuditReport(
                LocalDateTime.now().toString(),
                meals.size(),
                activeProducts.size(),
                activeVariants.size(),
                activeVariantIds.stream().mapToLong(id -> stockByVariantId.getOrDefault(id, 0L)).sum(),
                cookable,
                partial,
                notCookable,
                round(meals.isEmpty() ? 0.0 : (double) cookable / meals.size()),
                "Inventory schema has availableQuantity/reservedQuantity only; expiry/best-before cannot be audited because no expiry field exists.",
                topCounts(missingIngredientCounts, 30),
                topCounts(outOfStockIngredientCounts, 30),
                details
        );
    }

    private IngredientAudit auditIngredient(String ingredient, List<ProductCandidate> candidates) {
        String normalizedIngredient = normalizeText(ingredient);
        List<ProductCandidate> matched = candidates.stream()
                .filter(candidate -> ingredientMatchesProduct(ingredient, normalizedIngredient, candidate))
                .sorted(Comparator.comparingLong(ProductCandidate::stock).reversed())
                .toList();
        if (matched.isEmpty()) {
            return new IngredientAudit(ingredient, "MISSING_PRODUCT", null, null, 0L);
        }
        Optional<ProductCandidate> inStock = matched.stream()
                .filter(candidate -> candidate.stock() > 0)
                .findFirst();
        ProductCandidate selected = inStock.orElse(matched.get(0));
        return new IngredientAudit(
                ingredient,
                selected.stock() > 0 ? "IN_STOCK" : "OUT_OF_STOCK",
                selected.productId(),
                selected.productName(),
                selected.stock()
        );
    }

    private boolean ingredientMatchesProduct(String ingredient, String normalizedIngredient, ProductCandidate candidate) {
        if (normalizedIngredient.isBlank() || candidate.normalizedName().isBlank()) {
            return false;
        }
        String rawIngredient = normalizeSpacingPreservingAccents(ingredient);
        String normalizedName = candidate.normalizedName();
        if (isDerivedProductMismatch(normalizedIngredient, normalizedName)) {
            return false;
        }
        if (containsPhrase(candidate.searchName(), rawIngredient)) {
            return true;
        }
        List<String> tokens = Arrays.stream(normalizedIngredient.split("\\s+"))
                .filter(token -> token.length() >= 2)
                .toList();
        if (tokens.size() == 1) {
            if (containsVietnameseMarks(rawIngredient)) {
                return false;
            }
            return containsPhrase(normalizedName, tokens.get(0));
        }
        if (containsPhrase(normalizedName, normalizedIngredient)) {
            return true;
        }
        boolean allTokensInName = tokens.stream()
                .allMatch(token -> containsPhrase(normalizedName, token));
        if (allTokensInName) {
            return true;
        }
        return false;
    }

    private boolean isDerivedProductMismatch(String normalizedIngredient, String normalizedProductName) {
        List<String> derivedTerms = List.of("nuoc mam", "nuoc cot", "sot", "dau", "bot", "gia vi", "tuong", "hat nem");
        return derivedTerms.stream().anyMatch(term ->
                containsPhrase(normalizedProductName, term) && !containsPhrase(normalizedIngredient, term));
    }

    private ProductCandidate toCandidate(Product product, long stock) {
        String categoryName = "";
        try {
            categoryName = product.getCategory() != null && product.getCategory().getName() != null
                    ? product.getCategory().getName()
                    : "";
        } catch (Exception ignored) {
            categoryName = "";
        }
        String searchText = String.join(" ",
                product.getName() != null ? product.getName() : "",
                product.getShortDescription() != null ? product.getShortDescription() : "",
                product.getDescription() != null ? product.getDescription() : "",
                categoryName
        );
        return new ProductCandidate(
                product.getId(),
                product.getName(),
                normalizeSpacingPreservingAccents(product.getName()),
                normalizeText(product.getName()),
                normalizeText(searchText),
                stock
        );
    }

    private List<AuditMealItem> loadMeals() {
        try (InputStreamReader reader = new InputStreamReader(
                catalogResource.getInputStream(),
                java.nio.charset.StandardCharsets.UTF_8
        )) {
            return objectMapper.readValue(reader, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not load meal catalog for stock audit: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> normalizedIngredientList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private List<CountItem> topCounts(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> new CountItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean containsPhrase(String normalizedText, String normalizedPhrase) {
        return (" " + normalizedText + " ").contains(" " + normalizedPhrase + " ");
    }

    private boolean containsVietnameseMarks(String text) {
        return text != null && !Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .equals(text);
    }

    private String normalizeSpacingPreservingAccents(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
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

    private record ProductCandidate(
            Long productId,
            String productName,
            String searchName,
            String normalizedName,
            String normalizedSearchText,
            long stock
    ) {}

    public record MealCatalogAuditReport(
            String auditedAt,
            int totalMeals,
            int activeProducts,
            int activeVariants,
            long totalAvailableUnits,
            int cookableMeals,
            int partiallyCookableMeals,
            int notCookableMeals,
            double cookableRate,
            String expiryAuditNote,
            List<CountItem> topMissingIngredients,
            List<CountItem> topOutOfStockIngredients,
            List<MealAuditDetail> mealDetails
    ) {}

    public record CountItem(String ingredient, int count) {}

    public record MealAuditDetail(
            Integer mealId,
            String mealName,
            List<String> mealTypes,
            List<String> requiredIngredients,
            String status,
            double inStockCoverage,
            int inStockIngredientCount,
            int outOfStockIngredientCount,
            int missingIngredientCount,
            List<IngredientAudit> ingredients
    ) {}

    public record IngredientAudit(
            String ingredient,
            String status,
            Long matchedProductId,
            String matchedProductName,
            long availableUnits
    ) {}

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AuditMealItem {
        private Integer id;
        private String name;
        @JsonProperty("meal_type")
        private List<String> mealType;
        @JsonProperty("main_ingredients")
        private List<String> mainIngredients;
    }
}
