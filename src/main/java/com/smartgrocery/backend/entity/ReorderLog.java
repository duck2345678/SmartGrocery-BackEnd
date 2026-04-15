package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="reorder_logs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ReorderLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="user_id") private User user;
    @ManyToOne @JoinColumn(name="source_order_id") private Order sourceOrder;
    @ManyToOne @JoinColumn(name="new_order_id") private Order newOrder;
    private String reorderStatus;
    private LocalDateTime createdAt;
}