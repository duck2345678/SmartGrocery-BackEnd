package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="basket_optimization_items")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BasketOptimizationItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="optimization_id") private BasketOptimization optimization;
    @ManyToOne @JoinColumn(name="original_variant_id") private ProductVariant originalVariant;
    @ManyToOne @JoinColumn(name="suggested_variant_id") private ProductVariant suggestedVariant;
    private BigDecimal priceDiff;
    private Boolean isAccepted;
}
