package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.UserNutritionProfileDto;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserNutritionProfile;
import com.smartgrocery.backend.repository.jpa.UserNutritionProfileRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/users/{userId}/nutrition")
@Tag(name = "User Nutrition Management", description = "API quản lý hồ sơ dinh dưỡng người dùng")
public class UserNutritionController {

    @Autowired
    private UserNutritionProfileRepository nutritionProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Lấy hồ sơ dinh dưỡng của người dùng")
    public ResponseEntity<UserNutritionProfileDto> getNutritionProfile(@PathVariable Long userId) {
        com.smartgrocery.backend.security.SecurityUtils.verifyOwnershipOrAdmin(userId);
        return nutritionProfileRepository.findByUser_Id(userId)
                .map(this::convertToDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(UserNutritionProfileDto.builder().userId(userId).build()));
    }

    @PostMapping
    @Operation(summary = "Cập nhật hoặc tạo mới hồ sơ dinh dưỡng")
    public ResponseEntity<UserNutritionProfileDto> updateNutritionProfile(
            @PathVariable Long userId,
            @RequestBody UserNutritionProfileDto dto
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserNutritionProfile profile = nutritionProfileRepository.findByUser_Id(userId)
                .orElse(UserNutritionProfile.builder().user(user).build());

        profile.setHealthGoals(dto.getHealthGoals());
        profile.setDietaryPreference(dto.getDietaryPreference());
        profile.setAllergies(dto.getAllergies());
        profile.setFoodConstraints(dto.getFoodConstraints());
        profile.setHeightCm(dto.getHeight() != null ? java.math.BigDecimal.valueOf(dto.getHeight()) : null);
        profile.setWeightKg(dto.getWeight() != null ? java.math.BigDecimal.valueOf(dto.getWeight()) : null);
        
        // Calculate BMI
        if (dto.getHeight() != null && dto.getWeight() != null && dto.getHeight() > 0) {
            double heightInMeters = dto.getHeight() / 100.0;
            double bmi = dto.getWeight() / (heightInMeters * heightInMeters);
            profile.setBmi(java.math.BigDecimal.valueOf(bmi));
        }

        UserNutritionProfile saved = nutritionProfileRepository.save(profile);
        return ResponseEntity.ok(convertToDto(saved));
    }

    private UserNutritionProfileDto convertToDto(UserNutritionProfile entity) {
        return UserNutritionProfileDto.builder()
                .userId(entity.getUser().getId())
                .healthGoals(entity.getHealthGoals())
                .dietaryPreference(entity.getDietaryPreference())
                .allergies(entity.getAllergies())
                .foodConstraints(entity.getFoodConstraints())
                .height(entity.getHeightCm() != null ? entity.getHeightCm().doubleValue() : null)
                .weight(entity.getWeightKg() != null ? entity.getWeightKg().doubleValue() : null)
                .bmi(entity.getBmi() != null ? entity.getBmi().doubleValue() : null)
                .build();
    }
}
