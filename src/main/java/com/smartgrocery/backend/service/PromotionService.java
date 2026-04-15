package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.PromotionCampaignDto;
import com.smartgrocery.backend.entity.PromotionCampaign;
import com.smartgrocery.backend.repository.PromotionCampaignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class PromotionService {

    @Autowired
    private PromotionCampaignRepository promotionCampaignRepository;

    public List<PromotionCampaignDto> getAllCampaigns() {
        return promotionCampaignRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public PromotionCampaignDto createCampaign(PromotionCampaignDto dto) {
        PromotionCampaign campaign = PromotionCampaign.builder()
                .campaignCode(dto.getCampaignCode())
                .campaignName(dto.getCampaignName())
                .campaignType(dto.getCampaignType())
                .status("DRAFT")
                .startsAt(dto.getStartsAt())
                .endsAt(dto.getEndsAt())
                .build();
        return mapToDto(promotionCampaignRepository.save(campaign));
    }

    private PromotionCampaignDto mapToDto(PromotionCampaign c) {
        return PromotionCampaignDto.builder()
                .id(c.getId())
                .campaignCode(c.getCampaignCode())
                .campaignName(c.getCampaignName())
                .campaignType(c.getCampaignType())
                .status(c.getStatus())
                .startsAt(c.getStartsAt())
                .endsAt(c.getEndsAt())
                .build();
    }
}
