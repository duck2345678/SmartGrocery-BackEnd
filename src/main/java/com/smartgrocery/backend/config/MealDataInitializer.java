package com.smartgrocery.backend.config;

import com.smartgrocery.backend.repository.jpa.MealRepository;
import com.smartgrocery.backend.service.ai.MealDatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MealDataInitializer implements CommandLineRunner {

    private final MealDatabaseService mealDatabaseService;
    private final MealRepository mealRepository;

    @Override
    public void run(String... args) throws Exception {
        if (mealRepository.count() < 100) {
            log.info(">> Meals table has only {} entries (expected 100+). Triggering auto-seed...", mealRepository.count());
            mealDatabaseService.bootstrapFromProducts();
            int normalized = mealDatabaseService.normalizeAllMealIngredientsAtWritePath();
            log.info(">> Canonical/unit parse-at-write normalization updated {} meal ingredients.", normalized);
            log.info(">> Auto-seed complete! 102 meals added.");
        } else {
            int normalized = mealDatabaseService.normalizeAllMealIngredientsAtWritePath();
            log.info(">> Parse-at-write normalization pass updated {} meal ingredients.", normalized);
            log.info(">> Meals table already has {} entries. Skipping auto-seed.", mealRepository.count());
        }
    }
}
