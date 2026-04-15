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
@Table(name = "product_variants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 80, nullable = false, unique = true)
    private String sku;

    @Column(length = 80, unique = true)
    private String barcode;

    @Column(name = "variant_name", length = 120)
    private String variantName;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String unit = "unit";

    @Column(name = "package_size", length = 60)
    private String packageSize;

    @Column(name = "weight_gram")
    private Integer weightGram;

    @Column(name = "net_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal netPrice;

    @Column(name = "compare_at_price", precision = 15, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "cost_price", precision = 15, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "vat_percent", precision = 5, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal vatPercent = BigDecimal.ZERO;

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
