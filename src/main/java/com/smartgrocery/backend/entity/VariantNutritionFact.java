package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="variant_nutrition_facts")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class VariantNutritionFact {
    @Id private Long id;
    @OneToOne @MapsId @JoinColumn(name="variant_id") private ProductVariant variant;
    private BigDecimal servingSizeG;
    private BigDecimal caloriesPer100g;
    private BigDecimal proteinPer100g;
    private BigDecimal carbsPer100g;
    private BigDecimal fatPer100g;
    private BigDecimal fiberPer100g;
    private BigDecimal sugarPer100g;
    private BigDecimal sodiumMgPer100g;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
