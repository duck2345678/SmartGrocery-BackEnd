package com.smartgrocery.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PromotionCampaignDto {
    private Long id;
    private String campaignCode;
    private String campaignName;
    private String campaignType;
    private String status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
}
