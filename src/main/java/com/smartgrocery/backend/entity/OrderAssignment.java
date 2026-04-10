package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="order_assignments")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="order_id") private Order order;
    @ManyToOne @JoinColumn(name="staff_user_id") private User staff;
    private String taskType;
    private String status;
    private String proofImageUrl;
    @CreationTimestamp private LocalDateTime assignedAt;
    private LocalDateTime completedAt;
}