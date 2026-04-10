package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="order_status_histories")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="order_id") private Order order;
    private String status;
    @ManyToOne @JoinColumn(name="changed_by") private User changedBy;
    @CreationTimestamp private LocalDateTime changedAt;
}