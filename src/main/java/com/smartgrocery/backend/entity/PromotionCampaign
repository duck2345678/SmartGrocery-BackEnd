package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="promotion_campaigns")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PromotionCampaign {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String campaignCode;
    private String campaignName;
    private String campaignType;
    private String status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    @CreationTimestamp private LocalDateTime createdAt;
}