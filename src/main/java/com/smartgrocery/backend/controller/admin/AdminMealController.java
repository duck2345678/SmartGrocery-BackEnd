package com.smartgrocery.backend.controller.admin;

import com.smartgrocery.backend.service.ai.MealDatabaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/meals")
@RequiredArgsConstructor
public class AdminMealController {

    private final MealDatabaseService mealDatabaseService;

    @PostMapping("/compile")
    public ResponseEntity<?> compileMeals() {
        mealDatabaseService.bootstrapFromProducts();
        int normalized = mealDatabaseService.normalizeAllMealIngredientsAtWritePath();
        return ResponseEntity.ok(Map.of(
                "message", "Meal compilation started successfully based on existing products.",
                "normalizedMealIngredients", normalized
        ));
    }
}
