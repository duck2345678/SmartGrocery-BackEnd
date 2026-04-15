package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="meal_plan_items")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MealPlanItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="meal_plan_id") private MealPlan mealPlan;
    private Integer dayNo;
    private String mealSlot;
    @ManyToOne @JoinColumn(name="variant_id") private ProductVariant variant;
    private BigDecimal quantity;
    private BigDecimal estCalories;
}