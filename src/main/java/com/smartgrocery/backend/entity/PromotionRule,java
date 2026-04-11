package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="promotion_rules")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PromotionRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="campaign_id") private PromotionCampaign campaign;
    private String conditionType;
    private String conditionValue;
    private String effectType;
    private String effectValue;
}