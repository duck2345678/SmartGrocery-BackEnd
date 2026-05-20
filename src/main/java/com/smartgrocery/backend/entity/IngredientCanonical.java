package com.smartgrocery.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ingredient_canonical")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientCanonical {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_code", nullable = false, unique = true, length = 120)
    private String canonicalCode;

    @Column(name = "canonical_name_vi", nullable = false, length = 200)
    private String canonicalNameVi;

    @Column(name = "canonical_name_en", length = 200)
    private String canonicalNameEn;

    @Column(name = "ingredient_family", nullable = false, length = 80)
    private String ingredientFamily;

    @Column(name = "default_dimension", nullable = false, length = 20)
    private String defaultDimension;

    @Column(name = "average_weight_per_unit_g", precision = 12, scale = 4)
    private BigDecimal averageWeightPerUnitG;

    @Column(name = "average_volume_per_unit_ml", precision = 12, scale = 4)
    private BigDecimal averageVolumePerUnitMl;

    @Column(name = "density_g_per_ml", precision = 12, scale = 6)
    private BigDecimal densityGPerMl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
