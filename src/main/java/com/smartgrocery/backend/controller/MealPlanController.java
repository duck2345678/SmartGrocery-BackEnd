package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.entity.MealPlan;
import com.smartgrocery.backend.service.MealPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/meal-plans")
@Tag(name = "Meal Plan", description = "API quản lý kế hoạch ăn uống (AI-powered)")
public class MealPlanController {

    @Autowired
    private MealPlanService mealPlanService;

    @Operation(summary = "Lấy danh sách kế hoạch ăn uống của người dùng")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<MealPlan>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(mealPlanService.getByUserId(userId));
    }

    @Operation(summary = "Tạo thực đơn AI (MEMM-powered)")
    @PostMapping("/generate")
    public ResponseEntity<java.util.Map<String, Object>> generate(@RequestBody java.util.Map<String, Object> body) {
        Long userId = com.smartgrocery.backend.security.SecurityUtils.getCurrentUserId();
        String goal = body.get("goal") != null ? body.get("goal").toString() : null;
        MealPlanService.MealPlanGenerationResult result = mealPlanService.generateAIPlanStructured(userId, goal);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("mealPlan", result.getMealPlan());
        response.put("trustScore", result.getTrustScore());
        response.put("explanations", result.getExplanations());
        response.put("allergyWarnings", result.getAllergyWarnings());
        response.put("proposedItems", result.getProposedItems());
        return ResponseEntity.ok(response);
    }

}
