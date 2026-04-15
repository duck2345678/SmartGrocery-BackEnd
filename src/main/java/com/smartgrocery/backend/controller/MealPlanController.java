package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.entity.MealPlan;
import com.smartgrocery.backend.service.MealPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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

    @Operation(summary = "Yêu cầu AI tạo thực đơn 7 ngày mới")
    @PostMapping("/generate")
    public Mono<ResponseEntity<MealPlan>> generate(@RequestParam Long userId, @RequestParam String goal) {
        return mealPlanService.generateAIPlan(userId, goal)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }
}
