package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="recommendations")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Recommendation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="run_id") private RecommendationRun run;
    @ManyToOne @JoinColumn(name="user_id") private User user;
    @ManyToOne @JoinColumn(name="variant_id") private ProductVariant variant;
    private Integer rankNo;
    private BigDecimal score;
    private String reasonText;
    private LocalDateTime createdAt;
}