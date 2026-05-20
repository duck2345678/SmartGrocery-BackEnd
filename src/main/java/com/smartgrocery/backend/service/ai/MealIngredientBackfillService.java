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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MealIngredientBackfillService implements ApplicationRunner {

    private final MealIngredientRepository mealIngredientRepository;
    private final IngredientAliasRepository ingredientAliasRepository;
    private final QuantityParsingService quantityParsingService;
    private final IngredientTextNormalizer normalizer;

    @Value("${ai.matching.v2.backfill.enabled:true}")
    private boolean backfillEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!backfillEnabled) {
            return;
        }
        List<MealIngredient> ingredients = mealIngredientRepository.findAll();
        int updated = 0;
        for (MealIngredient ingredient : ingredients) {
            boolean changed = false;
            String sourceName = ingredient.getGenericName() != null && !ingredient.getGenericName().isBlank()
                    ? ingredient.getGenericName()
                    : (ingredient.getProduct() != null ? ingredient.getProduct().getName() : null);
            String normalizedName = normalizer.normalize(sourceName);
            if (!normalizedName.isBlank() && ingredient.getCanonicalIngredient() == null) {
                Optional<IngredientAlias> aliasOpt = ingredientAliasRepository
                        .findFirstByAliasTextNormAndLangAndActiveTrue(normalizedName, "vi");
                if (aliasOpt.isPresent()) {
                    ingredient.setCanonicalIngredient(aliasOpt.get().getCanonical());
                    changed = true;
                }
            }

            if (ingredient.getQuantity() != null && !ingredient.getQuantity().isBlank()) {
                QuantityParsingService.ParsedQuantity parsed = quantityParsingService.parse(ingredient.getQuantity());
                if (parsed.value() != null) {
                    ingredient.setQuantityValue(parsed.value());
                    changed = true;
                }
                if (parsed.unitRaw() != null && !parsed.unitRaw().isBlank()) {
                    ingredient.setQuantityUnitRaw(parsed.unitRaw());
                    changed = true;
                }
                if (parsed.unitCanonical() != null) {
                    ingredient.setQuantityUnitCanonical(parsed.unitCanonical());
                    changed = true;
                }
                ingredient.setQuantityParseStatus(parsed.status().name());
                ingredient.setQuantityParseConfidence(parsed.confidence() != null
                        ? parsed.confidence()
                        : BigDecimal.ZERO);
                changed = true;
            }

            if (changed) {
                mealIngredientRepository.save(ingredient);
                updated++;
            }
        }
        log.info("Meal ingredient canonical/unit backfill completed. updated={}", updated);
    }
}
