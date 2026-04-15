package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="reorder_events")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ReorderEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="user_id") private User user;
    @ManyToOne @JoinColumn(name="source_order_id") private Order sourceOrder;
    private String status;
    private LocalDateTime createdAt;
}