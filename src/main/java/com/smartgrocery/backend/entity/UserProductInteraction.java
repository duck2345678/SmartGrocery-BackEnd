package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="user_product_interactions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserProductInteraction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="user_id") private User user;
    @ManyToOne @JoinColumn(name="variant_id") private ProductVariant variant;
    private String interactionType;
    private Integer dwellTimeMs;
    private LocalDateTime createdAt;
}
