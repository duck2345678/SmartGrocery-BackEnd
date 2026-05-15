package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.RecommendationReplacementDto;
import com.smartgrocery.backend.entity.Recommendation;
import com.smartgrocery.backend.repository.jpa.RecommendationRepository;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.recommendation.FoodReplacementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recommendations")
@Tag(name = "Recommendations", description = "API cho gá»£i Ã½ sáº£n pháº©m thÃ´ng minh")
public class RecommendationController {

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Autowired
    private FoodReplacementService foodReplacementService;

    @GetMapping("/user/{userId}")
    @Operation(summary = "Lấy danh sách gợi ý cho người dùng")
    public ResponseEntity<List<Recommendation>> getUserRecommendations(@PathVariable Long userId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        return ResponseEntity.ok(recommendationRepository.findByUser_IdOrderByScoreDesc(userId));
    }

    @GetMapping("/substitutions/{variantId}")
    @Operation(summary = "Lấy tối đa 3 món thay thế gần nhất cho một biến thể")
    public ResponseEntity<List<RecommendationReplacementDto>> getNearestSubstitutions(
            @PathVariable Long variantId,
            @RequestParam(required = false) String dietaryPreference,
            @RequestParam(required = false) String allergies,
            @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(foodReplacementService.findNearestSubstitutes(variantId, dietaryPreference, allergies, limit));
    }
}
