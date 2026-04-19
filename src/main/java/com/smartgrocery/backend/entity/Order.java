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
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private UserAddress address;

    @Column(name = "order_number", length = 100, nullable = false, unique = true)
    private String orderNumber;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "PENDING"; // PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED

    @Column(name = "payment_method", length = 50, nullable = false)
    private String paymentMethod;

    @Column(name = "payment_status", length = 30, nullable = false)
    @Builder.Default
    private String paymentStatus = "UNPAID"; // UNPAID, PAID, REFUNDED

    @Column(name = "internal_note", length = 500)
    private String internalNote;

    @Column(name = "customer_note", length = 500)
    private String customerNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
