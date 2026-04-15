package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="ai_models")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AIModel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String modelCode;
    private String provider;
    private String modelName;
    private String modelType;
    private Boolean isActive;
    @CreationTimestamp private LocalDateTime createdAt;
}