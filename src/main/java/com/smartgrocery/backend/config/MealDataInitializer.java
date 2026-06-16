package com.smartgrocery.backend.config;

import com.smartgrocery.backend.repository.jpa.MealRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.service.SeedService;
import com.smartgrocery.backend.service.ai.MealDatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class MealDataInitializer implements CommandLineRunner {

    private final MealDatabaseService mealDatabaseService;
    private final MealRepository mealRepository;
    private final ProductRepository productRepository;
    private final SeedService seedService;

    @Override
    public void run(String... args) throws Exception {
        boolean needsProductSeed = productRepository.findByNameContainingIgnoreCase("Gạo nếp nương").isEmpty();
        boolean needsMealSeed = mealRepository.count() < 100;

        if (needsProductSeed || needsMealSeed) {
            if (needsProductSeed) {
                log.info(">> New products (Gạo nếp nương) not found in database. Seeding product catalog...");
                seedService.seedData();
            }
            log.info(">> Meals table has only {} entries or missing products. Triggering auto-seed...", mealRepository.count());
            mealDatabaseService.bootstrapFromProducts();
            int normalized = mealDatabaseService.normalizeAllMealIngredientsAtWritePath();
            log.info(">> Canonical/unit parse-at-write normalization updated {} meal ingredients.", normalized);
            log.info(">> Auto-seed complete!");
        } else {
            int normalized = mealDatabaseService.normalizeAllMealIngredientsAtWritePath();
            log.info(">> Parse-at-write normalization pass updated {} meal ingredients.", normalized);
            log.info(">> Meals table already has {} entries and products are up-to-date. Skipping auto-seed.", mealRepository.count());
        }
    }
}

