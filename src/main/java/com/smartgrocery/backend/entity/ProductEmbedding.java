package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="product_embeddings")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductEmbedding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne @JoinColumn(name="variant_id") private ProductVariant variant;
    @Column(columnDefinition="jsonb") private String vectorJson;
    private Integer vectorDim;
    private LocalDateTime createdAt;
}
