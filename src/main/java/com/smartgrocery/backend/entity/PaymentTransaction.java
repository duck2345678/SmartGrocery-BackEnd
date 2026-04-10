package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="payment_transactions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="payment_id") private Payment payment;
    private String txnType;
    private BigDecimal amount;
    private String status;
    @CreationTimestamp private LocalDateTime createdAt;
}