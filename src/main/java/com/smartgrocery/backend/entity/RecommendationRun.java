package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="recommendation_runs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RecommendationRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="user_id") private User user;
    private String triggerType;
    private String status;
    @CreationTimestamp private LocalDateTime startedAt;
    private Integer latencyMs;
}
