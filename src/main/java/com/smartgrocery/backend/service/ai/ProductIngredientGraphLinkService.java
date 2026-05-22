package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.IngredientAlias;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.repository.jpa.IngredientAliasRepository;
import com.smartgrocery.backend.repository.jpa.MealIngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIngredientGraphLinkService implements ApplicationRunner {

    private final MealIngredientRepository mealIngredientRepository;
    private final IngredientAliasRepository ingredientAliasRepository;
    private final IngredientTextNormalizer normalizer;
    private final CatalogSyncOutboxService outboxService;

    @Value("${ai.matching.v2.seed-product-ingredient-links:true}")
    private boolean linkEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!linkEnabled) {
            return;
        }
        log.info("Starting batch mapping of product-ingredient graph links...");
        List<MealIngredient> mealIngredients = mealIngredientRepository.findAll();
        if (mealIngredients.isEmpty()) {
            return;
        }

        // Cache all active Vietnamese ingredient aliases in memory to avoid N+1 queries
        List<IngredientAlias> allAliases = ingredientAliasRepository.findAll();
        Map<String, IngredientAlias> aliasMap = new HashMap<>();
        for (IngredientAlias alias : allAliases) {
            if (alias != null && Boolean.TRUE.equals(alias.getActive()) && "vi".equalsIgnoreCase(alias.getLang()) && alias.getAliasTextNorm() != null) {
                if (alias.getCanonical() != null && alias.getCanonical().getId() != null) {
                    // Keep the first alias mapped to canonical
                    aliasMap.putIfAbsent(alias.getAliasTextNorm(), alias);
                }
            }
        }

        // Fetch already enqueued meal ingredient IDs to prevent duplicates
        List<Long> existingIds = outboxService.getExistingAggregateIds("PRODUCT_INGREDIENT_MATCH");
        java.util.Set<Long> existingSet = new java.util.HashSet<>(existingIds);

        List<CatalogSyncOutboxService.ProductIngredientMatchDto> newMatches = new java.util.ArrayList<>();
        int skipped = 0;
        for (MealIngredient mi : mealIngredients) {
            if (mi == null || mi.getProduct() == null || mi.getProduct().getId() == null) {
                continue;
            }
            // Skip if this meal ingredient link has already been mapped and enqueued
            if (existingSet.contains(mi.getId())) {
                skipped++;
                continue;
            }
            String sourceName = mi.getGenericName() != null ? mi.getGenericName() : mi.getProduct().getName();
            String aliasNorm = normalizer.normalize(sourceName);
            if (aliasNorm.isBlank()) continue;

            IngredientAlias aliasOpt = aliasMap.get(aliasNorm);
            if (aliasOpt == null) {
                continue;
            }
            newMatches.add(new CatalogSyncOutboxService.ProductIngredientMatchDto(
                    mi.getId(),
                    mi.getProduct().getId(),
                    aliasOpt.getCanonical().getId()
            ));
        }

        if (!newMatches.isEmpty()) {
            outboxService.enqueueMatches(newMatches);
            log.info("Enqueued {} new product-ingredient graph links in a single batch (Skipped {} duplicates)", newMatches.size(), skipped);
        } else {
            log.info("All {} product-ingredient graph links are already mapped and synchronized (Skipped {} duplicates)", mealIngredients.size(), skipped);
        }
    }
}
