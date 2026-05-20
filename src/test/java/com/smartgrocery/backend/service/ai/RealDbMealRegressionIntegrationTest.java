package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.repository.jpa.IngredientAliasRepository;
import com.smartgrocery.backend.repository.jpa.MealIngredientRepository;
import com.smartgrocery.backend.repository.jpa.MealRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SpringBootTest
@EnabledIf("isDbConfigured")
class RealDbMealRegressionIntegrationTest {

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private MealIngredientRepository mealIngredientRepository;

    @Autowired
    private IngredientAliasRepository ingredientAliasRepository;

    @Autowired
    private IngredientTextNormalizer normalizer;

    static boolean isDbConfigured() {
        if (hasEnv("SUPABASE_DB_URL") && hasEnv("SUPABASE_DB_USERNAME") && hasEnv("SUPABASE_DB_PASSWORD")) {
            return true;
        }
        Path dotEnv = Path.of(".env");
        if (!Files.exists(dotEnv)) {
            return false;
        }
        try (Stream<String> lines = Files.lines(dotEnv, StandardCharsets.UTF_8)) {
            List<String> all = lines.toList();
            return hasDotEnvKey(all, "SUPABASE_DB_URL")
                    && hasDotEnvKey(all, "SUPABASE_DB_USERNAME")
                    && hasDotEnvKey(all, "SUPABASE_DB_PASSWORD");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasDotEnvKey(List<String> lines, String key) {
        String prefix = key + "=";
        return lines.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .filter(s -> !s.startsWith("#"))
                .anyMatch(s -> s.startsWith(prefix) && s.length() > prefix.length());
    }

    private static boolean hasEnv(String key) {
        String value = System.getenv(key);
        return value != null && !value.isBlank();
    }

    @Test
    void shouldGeneratePassFailReportPerMealFromRealDb() throws Exception {
        List<Meal> meals = mealRepository.findAll().stream()
                .sorted(Comparator.comparing(Meal::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        assertEquals(114, meals.size(), "Expected exactly 114 meals in DB for this regression run");

        List<Map<String, Object>> mealReports = new ArrayList<>();
        int passMeals = 0;
        int failMeals = 0;

        for (Meal meal : meals) {
            List<MealIngredient> ingredients = mealIngredientRepository.findByMealIdWithProduct(meal.getId());
            MealCheckResult check = checkMeal(meal, ingredients);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mealId", meal.getId());
            row.put("mealName", meal.getName());
            row.put("category", meal.getCategory());
            row.put("dietaryGoal", meal.getDietaryGoal());
            row.put("pass", check.pass());
            row.put("failedRules", check.failedRules());
            row.put("warnings", check.warnings());
            row.put("ingredientCount", ingredients.size());
            row.put("ingredientResults", check.ingredientResults());
            mealReports.add(row);

            if (check.pass()) {
                passMeals++;
            } else {
                failMeals++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", LocalDateTime.now().toString());
        summary.put("mealCount", meals.size());
        summary.put("passMeals", passMeals);
        summary.put("failMeals", failMeals);
        summary.put("passRate", meals.isEmpty() ? 0.0 : ((double) passMeals / (double) meals.size()));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", summary);
        report.put("meals", mealReports);

        Path output = Path.of("target", "reports", "meal-regression-realdb-114.json");
        Files.createDirectories(output.getParent());
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Files.writeString(output, mapper.writeValueAsString(report), StandardCharsets.UTF_8);

        log.info("Real DB meal regression report generated at {}", output.toAbsolutePath());
        assertTrue(Files.exists(output), "Report file must be generated");
    }

    private MealCheckResult checkMeal(Meal meal, List<MealIngredient> ingredients) {
        List<String> failedRules = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> ingredientRows = new ArrayList<>();

        if (ingredients == null || ingredients.isEmpty()) {
            failedRules.add("NO_INGREDIENTS");
            return new MealCheckResult(false, failedRules, warnings, ingredientRows);
        }

        for (MealIngredient ingredient : ingredients) {
            Map<String, Object> row = new LinkedHashMap<>();
            String sourceName = ingredient.getGenericName();
            if ((sourceName == null || sourceName.isBlank()) && ingredient.getProduct() != null) {
                sourceName = ingredient.getProduct().getName();
            }
            String normalizedName = normalizer.normalize(sourceName);

            row.put("mealIngredientId", ingredient.getId());
            row.put("sourceName", sourceName);
            row.put("normalizedName", normalizedName);
            row.put("productName", ingredient.getProduct() == null ? null : ingredient.getProduct().getName());
            row.put("canonicalCode", ingredient.getCanonicalIngredient() == null ? null : ingredient.getCanonicalIngredient().getCanonicalCode());
            row.put("quantity", ingredient.getQuantity());
            row.put("quantityValue", ingredient.getQuantityValue());
            row.put("quantityUnitRaw", ingredient.getQuantityUnitRaw());
            row.put("quantityParseStatus", ingredient.getQuantityParseStatus());
            row.put("quantityParseConfidence", ingredient.getQuantityParseConfidence());
            row.put("quantityUnitCanonical", ingredient.getQuantityUnitCanonical() == null ? null : ingredient.getQuantityUnitCanonical().getUnitCode());

            if (ingredient.getCanonicalIngredient() == null) {
                Optional<?> aliasHit = ingredientAliasRepository
                        .findFirstByAliasTextNormAndLangAndActiveTrue(normalizedName, "vi");
                if (aliasHit.isPresent()) {
                    warnings.add("MISSING_CANONICAL_FIELD_BUT_ALIAS_EXISTS");
                    row.put("canonicalCheck", "WARN_ALIAS_EXISTS");
                } else {
                    failedRules.add("CANONICAL_ALIAS_NOT_FOUND");
                    row.put("canonicalCheck", "FAIL_ALIAS_NOT_FOUND");
                }
            } else {
                row.put("canonicalCheck", "PASS");
            }

            if (ingredient.getQuantity() != null && !ingredient.getQuantity().isBlank()) {
                String status = ingredient.getQuantityParseStatus() == null ? "" : ingredient.getQuantityParseStatus().trim();
                boolean parsedOk = "PARSED".equalsIgnoreCase(status) || "APPROX".equalsIgnoreCase(status);
                if (!parsedOk) {
                    failedRules.add("QUANTITY_PARSE_NOT_OK");
                    row.put("quantityCheck", "FAIL_STATUS_" + status);
                } else if (ingredient.getQuantityValue() == null || ingredient.getQuantityUnitCanonical() == null) {
                    failedRules.add("QUANTITY_STRUCTURED_FIELDS_MISSING");
                    row.put("quantityCheck", "FAIL_STRUCTURED_FIELDS");
                } else {
                    row.put("quantityCheck", "PASS");
                }
            } else {
                row.put("quantityCheck", "SKIP_EMPTY_QUANTITY");
            }

            if (normalizedName.contains("ot hiem")) {
                String productName = ingredient.getProduct() == null ? "" : normalizer.normalize(ingredient.getProduct().getName());
                if (productName.contains("cherry")) {
                    failedRules.add("OT_HIEM_MAPPED_TO_CHERRY");
                    row.put("sanityCheck", "FAIL_OT_HIEM_CHERRY");
                }
            }

            ingredientRows.add(row);
        }

        String mealNameNorm = normalizer.normalize(meal.getName());
        if (mealNameNorm.contains("canh cai") && mealNameNorm.contains("thit bam")) {
            boolean hasRauCai = ingredients.stream().anyMatch(mi -> {
                String n = normalizer.normalize(
                        mi.getGenericName() != null ? mi.getGenericName()
                                : (mi.getProduct() != null ? mi.getProduct().getName() : "")
                );
                return n.contains("cai") || n.contains("rau cai");
            });
            if (!hasRauCai) {
                failedRules.add("CANH_CAI_MISSING_RAU_CAI");
            }
        }

        if (failedRules.isEmpty()) {
            warnings.add("PASS_ALL_RULES");
        }
        return new MealCheckResult(failedRules.isEmpty(), failedRules, warnings, ingredientRows);
    }

    private record MealCheckResult(
            boolean pass,
            List<String> failedRules,
            List<String> warnings,
            List<Map<String, Object>> ingredientResults
    ) {
    }
}
