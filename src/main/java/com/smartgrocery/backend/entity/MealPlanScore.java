package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="meal_plan_scores")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MealPlanScore {
    @Id private Long id;
    @OneToOne @MapsId @JoinColumn(name="meal_plan_id") private MealPlan mealPlan;
    private BigDecimal nutritionFit;
    private BigDecimal budgetFit;
    private String explainText;
}