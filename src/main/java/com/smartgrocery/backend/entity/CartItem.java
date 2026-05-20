package com.smartgrocery.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "variant_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;
    @Column(name = "allow_substitution", nullable = false)
    @Builder.Default
    private Boolean allowSubstitution = false;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String source = "MANUAL";

    @Column(name = "ai_list_code", length = 80, nullable = false)
    @Builder.Default
    private String aiListCode = "";

    @Column(name = "ai_list_name", length = 160)
    private String aiListName;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;
}
