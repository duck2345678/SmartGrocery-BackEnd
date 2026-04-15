package com.smartgrocery.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "meal_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meal_plan_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private RecommendationRun recommendationRun;

    @Column(name = "title", length = 180, nullable = false)
    private String title;

    @Column(name = "plan_days", nullable = false)
    @Builder.Default
    private Integer planDays = 7;

    @Column(name = "budget_limit", precision = 15, scale = 2)
    private BigDecimal budgetLimit;

    @Column(name = "calories_target", precision = 8, scale = 2)
    private BigDecimal caloriesTarget;

    @Column(name = "score_total", precision = 10, scale = 4)
    private BigDecimal scoreTotal;

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "DRAFT"; // DRAFT, RECOMMENDED, ACCEPTED, ARCHIVED

    @OneToMany(mappedBy = "mealPlan", cascade = CascadeType.ALL)
    private List<MealPlanItem> items;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
