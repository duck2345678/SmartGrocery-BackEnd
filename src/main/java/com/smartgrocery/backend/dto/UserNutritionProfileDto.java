package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNutritionProfileDto {
    private Long userId;
    private String healthGoals;
    private String dietaryPreference;
    private String allergies;
    private Double height;
    private Double weight;
    private Double bmi;
}
