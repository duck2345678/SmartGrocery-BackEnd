package com.smartgrocery.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representing the "Student" (Học sinh/Sản phẩm trong món ăn)
 */
@Entity
@Table(name = "meal_ingredients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meal_ingredient_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "generic_name", length = 100)
    private String genericName;

    @Column(length = 20) // PRIMARY, SECONDARY
    private String role;

    @Column
    private String quantity; // e.g., "500g", "2 items"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_ingredient_id")
    private IngredientCanonical canonicalIngredient;

    @Column(name = "quantity_value", precision = 12, scale = 4)
    private java.math.BigDecimal quantityValue;

    @Column(name = "quantity_unit_raw", length = 80)
    private String quantityUnitRaw;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quantity_unit_canonical_id")
    private UnitCanonical quantityUnitCanonical;

    @Column(name = "quantity_parse_status", nullable = false, length = 20)
    @Builder.Default
    private String quantityParseStatus = "UNPARSED";

    @Column(name = "quantity_parse_confidence", precision = 5, scale = 4)
    private java.math.BigDecimal quantityParseConfidence;

    @Column(name = "is_mandatory", nullable = false)
    @Builder.Default
    private Boolean isMandatory = true;
}
