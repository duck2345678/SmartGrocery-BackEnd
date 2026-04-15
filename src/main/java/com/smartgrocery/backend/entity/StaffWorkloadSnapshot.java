package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="staff_workload_snapshots")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StaffWorkloadSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="staff_user_id") private User staff;
    private Integer activeOrders;
    private Double loadScore;
    @CreationTimestamp private LocalDateTime snapshotAt;
}