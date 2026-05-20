package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.IngredientAlias;
import com.smartgrocery.backend.entity.IngredientCanonical;
import com.smartgrocery.backend.entity.UnitAlias;
import com.smartgrocery.backend.entity.UnitCanonical;
import com.smartgrocery.backend.repository.jpa.IngredientAliasRepository;
import com.smartgrocery.backend.repository.jpa.IngredientCanonicalRepository;
import com.smartgrocery.backend.repository.jpa.MealIngredientRepository;
import com.smartgrocery.backend.repository.jpa.UnitAliasRepository;
import com.smartgrocery.backend.repository.jpa.UnitCanonicalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class CanonicalDictionarySeedService implements ApplicationRunner {

    private final IngredientCanonicalRepository ingredientCanonicalRepository;
    private final IngredientAliasRepository ingredientAliasRepository;
    private final MealIngredientRepository mealIngredientRepository;
    private final UnitCanonicalRepository unitCanonicalRepository;
    private final UnitAliasRepository unitAliasRepository;
    private final CatalogSyncOutboxService outboxService;
    private final IngredientTextNormalizer normalizer;

    @Value("${ai.matching.v2.seed-dictionaries:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        seedUnits();
        seedIngredients();
        seedDynamicIngredientAliasesFromMeals();
    }

    private void seedUnits() {
        ensureUnit("g", "mass", new BigDecimal("1"), "g", false, null, null, List.of("g", "gram"));
        ensureUnit("kg", "mass", new BigDecimal("1000"), "g", false, null, null, List.of("kg", "kilo", "ki", "ky"));
        ensureUnit("ml", "volume", new BigDecimal("1"), "ml", false, null, null, List.of("ml"));
        ensureUnit("l", "volume", new BigDecimal("1000"), "ml", false, null, null, List.of("l", "lit", "lít"));
        ensureUnit("piece", "count", BigDecimal.ONE, "piece", false, null, null, List.of("qua", "trai", "mieng", "cai"));
        ensureUnit("pinch", "mass", BigDecimal.ONE, "g", true, new BigDecimal("5"), null, List.of("nhum", "nhum nho"));
        ensureUnit("bunch", "mass", BigDecimal.ONE, "g", true, new BigDecimal("100"), null, List.of("bo", "bo nho"));
    }

    private void ensureUnit(
            String unitCode,
            String dimension,
            BigDecimal factor,
            String baseUnit,
            boolean approximate,
            BigDecimal defaultMass,
            BigDecimal defaultVolume,
            List<String> aliases
    ) {
        boolean isNew = false;
        UnitCanonical canonical = unitCanonicalRepository.findByUnitCode(unitCode).orElse(null);
        if (canonical == null) {
            canonical = unitCanonicalRepository.save(UnitCanonical.builder()
                    .unitCode(unitCode)
                    .dimension(dimension)
                    .toBaseFactor(factor)
                    .baseUnitCode(baseUnit)
                    .approximate(approximate)
                    .defaultMassG(defaultMass)
                    .defaultVolumeMl(defaultVolume)
                    .active(true)
                    .build());
            isNew = true;
        }
        if (isNew) {
            outboxService.enqueue("UNIT_CANONICAL", canonical.getId(), "UPSERT", Map.of(
                    "unitCode", canonical.getUnitCode(),
                    "dimension", canonical.getDimension(),
                    "baseUnitCode", canonical.getBaseUnitCode(),
                    "factor", canonical.getToBaseFactor(),
                    "approximate", canonical.getApproximate(),
                    "active", canonical.getActive()
            ));
        }
        for (String alias : aliases) {
            String norm = normalizer.normalize(alias);
            if (norm.isBlank()) continue;
            if (unitAliasRepository.findFirstByAliasTextNormAndLocaleAndActiveTrue(norm, "vi").isPresent()) {
                continue;
            }
            UnitAlias unitAlias = unitAliasRepository.save(UnitAlias.builder()
                    .unitCanonical(canonical)
                    .aliasTextRaw(alias)
                    .aliasTextNorm(norm)
                    .locale("vi")
                    .source("seed")
                    .confidence(BigDecimal.ONE)
                    .active(true)
                    .build());
            outboxService.enqueue("UNIT_ALIAS", unitAlias.getId(), "UPSERT", Map.of(
                    "aliasNorm", unitAlias.getAliasTextNorm(),
                    "aliasRaw", unitAlias.getAliasTextRaw(),
                    "locale", unitAlias.getLocale(),
                    "confidence", unitAlias.getConfidence(),
                    "active", unitAlias.getActive(),
                    "unitCode", canonical.getUnitCode()
            ));
        }
    }

    private void seedIngredients() {
        ensureIngredient(
                "ot_hiem",
                "ớt hiểm",
                "spice",
                "count",
                new BigDecimal("4"),
                null,
                List.of("ớt hiểm", "ot hiem", "chili bird eye", "bird eye chili")
        );
        ensureIngredient(
                "nghe_bot",
                "bột nghệ",
                "spice",
                "mass",
                null,
                null,
                List.of("bột nghệ", "nghệ bột", "turmeric powder", "bot nghe")
        );
        ensureIngredient(
                "rau_cai",
                "rau cải",
                "vegetable",
                "mass",
                new BigDecimal("200"),
                null,
                List.of("rau cải", "cải thìa", "cải bẹ", "cai", "rau cai")
        );
        ensureIngredient(
                "ca_chua",
                "cà chua",
                "vegetable",
                "count",
                new BigDecimal("150"),
                null,
                List.of("cà chua", "ca chua", "tomato")
        );
    }

    private void ensureIngredient(
            String code,
            String nameVi,
            String family,
            String dimension,
            BigDecimal avgWeight,
            BigDecimal avgVolume,
            List<String> aliases
    ) {
        boolean isNew = false;
        IngredientCanonical canonical = ingredientCanonicalRepository.findByCanonicalCode(code).orElse(null);
        if (canonical == null) {
            canonical = ingredientCanonicalRepository.save(IngredientCanonical.builder()
                    .canonicalCode(code)
                    .canonicalNameVi(nameVi)
                    .canonicalNameEn(nameVi.toLowerCase(Locale.ROOT))
                    .ingredientFamily(family)
                    .defaultDimension(dimension)
                    .averageWeightPerUnitG(avgWeight)
                    .averageVolumePerUnitMl(avgVolume)
                    .active(true)
                    .build());
            isNew = true;
        }
        if (isNew) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", canonical.getId());
            payload.put("code", canonical.getCanonicalCode());
            payload.put("nameVi", canonical.getCanonicalNameVi());
            payload.put("family", canonical.getIngredientFamily());
            payload.put("dimension", canonical.getDefaultDimension());
            payload.put("avgWeightG", canonical.getAverageWeightPerUnitG());
            payload.put("avgVolumeMl", canonical.getAverageVolumePerUnitMl());
            payload.put("active", canonical.getActive());
            outboxService.enqueue("INGREDIENT_CANONICAL", canonical.getId(), "UPSERT", payload);
        }

        for (String alias : aliases) {
            String norm = normalizer.normalize(alias);
            if (norm.isBlank()) continue;
            if (ingredientAliasRepository.findFirstByAliasTextNormAndLangAndActiveTrue(norm, "vi").isPresent()) {
                continue;
            }
            IngredientAlias ingredientAlias = ingredientAliasRepository.save(IngredientAlias.builder()
                    .canonical(canonical)
                    .aliasTextRaw(alias)
                    .aliasTextNorm(norm)
                    .lang("vi")
                    .source("seed")
                    .confidence(BigDecimal.ONE)
                    .active(true)
                    .build());
            outboxService.enqueue("INGREDIENT_ALIAS", ingredientAlias.getId(), "UPSERT", Map.of(
                    "aliasNorm", ingredientAlias.getAliasTextNorm(),
                    "aliasRaw", ingredientAlias.getAliasTextRaw(),
                    "lang", ingredientAlias.getLang(),
                    "confidence", ingredientAlias.getConfidence(),
                    "active", ingredientAlias.getActive(),
                    "canonicalId", canonical.getId()
            ));
        }
    }

    private void seedDynamicIngredientAliasesFromMeals() {
        List<String> sourceNames = mealIngredientRepository.findDistinctIngredientSourceNames();
        int createdCanonical = 0;
        int createdAlias = 0;
        for (String sourceName : sourceNames) {
            if (sourceName == null || sourceName.isBlank()) {
                continue;
            }
            String norm = normalizer.normalize(sourceName);
            if (norm.isBlank()) {
                continue;
            }
            if (ingredientAliasRepository.findFirstByAliasTextNormAndLangAndActiveTrue(norm, "vi").isPresent()) {
                continue;
            }

            String canonicalCode = "auto_" + slug(norm);
            IngredientCanonical canonical = ingredientCanonicalRepository.findByCanonicalCode(canonicalCode).orElse(null);
            if (canonical == null) {
                canonical = ingredientCanonicalRepository.save(IngredientCanonical.builder()
                        .canonicalCode(canonicalCode)
                        .canonicalNameVi(sourceName.trim())
                        .canonicalNameEn(norm)
                        .ingredientFamily(guessFamily(norm))
                        .defaultDimension("count")
                        .averageWeightPerUnitG(null)
                        .averageVolumePerUnitMl(null)
                        .active(true)
                        .build());
                createdCanonical++;

                Map<String, Object> canonicalPayload = new LinkedHashMap<>();
                canonicalPayload.put("id", canonical.getId());
                canonicalPayload.put("code", canonical.getCanonicalCode());
                canonicalPayload.put("nameVi", canonical.getCanonicalNameVi());
                canonicalPayload.put("family", canonical.getIngredientFamily());
                canonicalPayload.put("dimension", canonical.getDefaultDimension());
                canonicalPayload.put("avgWeightG", canonical.getAverageWeightPerUnitG());
                canonicalPayload.put("avgVolumeMl", canonical.getAverageVolumePerUnitMl());
                canonicalPayload.put("active", canonical.getActive());
                outboxService.enqueue("INGREDIENT_CANONICAL", canonical.getId(), "UPSERT", canonicalPayload);
            }

            IngredientAlias alias = ingredientAliasRepository.save(IngredientAlias.builder()
                    .canonical(canonical)
                    .aliasTextRaw(sourceName.trim())
                    .aliasTextNorm(norm)
                    .lang("vi")
                    .source("import")
                    .confidence(new BigDecimal("0.9000"))
                    .active(true)
                    .build());
            createdAlias++;

            Map<String, Object> aliasPayload = new LinkedHashMap<>();
            aliasPayload.put("aliasNorm", alias.getAliasTextNorm());
            aliasPayload.put("aliasRaw", alias.getAliasTextRaw());
            aliasPayload.put("lang", alias.getLang());
            aliasPayload.put("confidence", alias.getConfidence());
            aliasPayload.put("active", alias.getActive());
            aliasPayload.put("canonicalId", canonical.getId());
            outboxService.enqueue("INGREDIENT_ALIAS", alias.getId(), "UPSERT", aliasPayload);
        }
        if (createdCanonical > 0 || createdAlias > 0) {
            log.info("Dynamic ingredient dictionary seeded from meals. canonicalCreated={}, aliasCreated={}",
                    createdCanonical, createdAlias);
        }
    }

    private String slug(String norm) {
        String slug = norm.replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isBlank()) {
            return "ingredient";
        }
        return slug.length() > 100 ? slug.substring(0, 100) : slug;
    }

    private String guessFamily(String norm) {
        String n = Objects.requireNonNullElse(norm, "");
        if (n.contains("thit") || n.contains("bo ") || n.contains("ga ") || n.contains("heo")) {
            return "meat";
        }
        if (n.contains("ca ") || n.contains("tom") || n.contains("muc") || n.contains("bach tuoc") || n.contains("ngheu")) {
            return "seafood";
        }
        if (n.contains("ot") || n.contains("tieu") || n.contains("nghe") || n.contains("toi") || n.contains("hanh")) {
            return "spice";
        }
        if (n.contains("dau ") || n.contains("nuoc")) {
            return "sauce";
        }
        return "other";
    }
}
