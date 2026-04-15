package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="basket_optimizations")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BasketOptimization {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="user_id") private User user;
    @ManyToOne @JoinColumn(name="cart_id") private Cart cart;
    private BigDecimal originalTotal;
    private BigDecimal optimizedTotal;
    private LocalDateTime createdAt;
}