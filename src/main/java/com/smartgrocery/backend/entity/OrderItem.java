package com.smartgrocery.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "product_name", length = 200, nullable = false)
    private String productName;

    @Column(name = "variant_name", length = 120)
    private String variantName;

    @Column(length = 80, nullable = false)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;


    @Column(name = "picked_quantity")
    private Integer pickedQuantity;

    @Column(name = "is_substituted", nullable = false)
    @Builder.Default
    private Boolean isSubstituted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "substituted_variant_id")
    private ProductVariant substitutedVariant;

    @Column(name = "substitution_reason", length = 255)
    private String substitutionReason;

    @Column(name = "total_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalPrice;
}
