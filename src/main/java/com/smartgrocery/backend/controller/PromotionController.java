package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.PromotionCampaignDto;
import com.smartgrocery.backend.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/promotions")
@Tag(name = "Admin - Promotion", description = "Quản lý Khuyến mãi")
public class PromotionController {

    @Autowired
    private PromotionService promotionService;

    @Operation(summary = "Lấy danh sách chiến dịch khuyến mãi")
    @GetMapping("/campaigns")
    public ResponseEntity<List<PromotionCampaignDto>> getAllCampaigns() {
        return ResponseEntity.ok(promotionService.getAllCampaigns());
    }

    @Operation(summary = "Tạo chiến dịch mới")
    @PostMapping("/campaigns")
    public ResponseEntity<PromotionCampaignDto> createCampaign(@RequestBody PromotionCampaignDto dto) {
        return ResponseEntity.ok(promotionService.createCampaign(dto));
    }
}
