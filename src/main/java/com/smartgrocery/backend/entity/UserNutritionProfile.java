package com.smartgrocery.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_nutrition_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNutritionProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "dietary_preference", length = 100)
    private String dietaryPreference;

    @Column(length = 255)
    private String allergies;

    @Column(name = "health_goals", length = 255)
    private String healthGoals;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "food_constraints", columnDefinition = "jsonb")
    private String foodConstraints;

    @Column(precision = 5, scale = 2)
    private BigDecimal bmi;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "daily_calorie_target")
    private Integer dailyCalorieTarget;

    @Column(name = "ai_interaction_points", nullable = false)
    @Builder.Default
    private Integer aiInteractionPoints = 0;

    @Column(name = "received_first_order_reward", nullable = false)
    @Builder.Default
    private Boolean receivedFirstOrderReward = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
