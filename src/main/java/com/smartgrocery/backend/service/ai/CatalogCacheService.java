package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.ShoppingScenarioAlias;
import com.smartgrocery.backend.repository.jpa.MealIngredientRepository;
import com.smartgrocery.backend.repository.jpa.MealRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.ShoppingScenarioAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogCacheService {

    private final MealRepository mealRepository;
    private final MealIngredientRepository mealIngredientRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ShoppingScenarioAliasRepository shoppingScenarioAliasRepository;

    private volatile List<Meal> cachedMeals = null;
    private volatile Map<Long, List<MealIngredient>> cachedIngredientsByMeal = null;
    private volatile List<ProductVariant> cachedDiscountedVariants = null;
    private volatile List<ShoppingScenarioAlias> cachedScenarioAliases = null;
    private volatile long catalogCacheExpiry = 0L;
    private static final long CATALOG_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    public void ensureCatalogCached() {
        if (System.currentTimeMillis() < catalogCacheExpiry && cachedMeals != null) {
            return;
        }
        synchronized (this) {
            if (System.currentTimeMillis() < catalogCacheExpiry && cachedMeals != null) {
                return;
            }
            long t0 = System.currentTimeMillis();

            List<Meal> meals = mealRepository.findAll();

            List<MealIngredient> allIngredients = mealIngredientRepository.findAllWithProduct();
            Map<Long, List<MealIngredient>> byMeal = new HashMap<>();
            for (MealIngredient mi : allIngredients) {
                byMeal.computeIfAbsent(mi.getMeal().getId(), k -> new ArrayList<>()).add(mi);
            }

            List<ProductVariant> discounts = productVariantRepository.findTop10DiscountedVariants();
            List<ShoppingScenarioAlias> aliases = shoppingScenarioAliasRepository.findActiveAliases();

            cachedMeals = meals;
            cachedIngredientsByMeal = byMeal;
            cachedDiscountedVariants = discounts;
            cachedScenarioAliases = aliases;
            catalogCacheExpiry = System.currentTimeMillis() + CATALOG_CACHE_TTL_MS;
            log.info("[CatalogCache] Rebuilt: {} meals, {} ingredient rows, {} discounts, {} aliases in {}ms",
                    meals.size(), allIngredients.size(), discounts.size(), aliases.size(), System.currentTimeMillis() - t0);
        }
    }

    public List<Meal> getCachedMeals() {
        ensureCatalogCached();
        return cachedMeals;
    }

    public Map<Long, List<MealIngredient>> getCachedIngredientsByMeal() {
        ensureCatalogCached();
        return cachedIngredientsByMeal;
    }

    public List<ProductVariant> getCachedDiscountedVariants() {
        ensureCatalogCached();
        return cachedDiscountedVariants;
    }

    public List<ShoppingScenarioAlias> getCachedScenarioAliases() {
        ensureCatalogCached();
        return cachedScenarioAliases;
    }
}
