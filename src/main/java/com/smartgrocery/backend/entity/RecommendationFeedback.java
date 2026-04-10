package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="recommendation_feedbacks")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RecommendationFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="recommendation_id") private Recommendation recommendation;
    private String feedbackType;
    private Integer feedbackScore;
    private LocalDateTime createdAt;
}