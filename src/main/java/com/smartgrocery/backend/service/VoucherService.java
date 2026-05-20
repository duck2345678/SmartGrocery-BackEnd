package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.VoucherDto;
import com.smartgrocery.backend.dto.VoucherGenerationRequest;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.Voucher;
import com.smartgrocery.backend.repository.jpa.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;

    @Transactional(readOnly = true)
    public List<VoucherDto> getAvailableVouchers() {
        LocalDateTime now = LocalDateTime.now();
        return voucherRepository.findAvailableAt(now)
                .stream()
                .filter(v -> v.getUsageLimit() == null || v.getUsageCount() == null || v.getUsageCount() < v.getUsageLimit())
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VoucherDto> getAvailableVouchers(User user) {
        LocalDateTime now = LocalDateTime.now();
        return voucherRepository.findVisibleForUserAt(user.getId(), now)
                .stream()
                .filter(v -> v.getUsageLimit() == null || v.getUsageCount() == null || v.getUsageCount() < v.getUsageLimit())
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VoucherDto> getAllVouchers() {
        return voucherRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<VoucherDto> generateVouchers(VoucherGenerationRequest request) {
        List<Voucher> created = new ArrayList<>();
        String prefix = request.getPrefix() != null ? request.getPrefix().toUpperCase() : "SG";
        
        for (int i = 0; i < request.getQuantity(); i++) {
            String code = generateUniqueCode(prefix);
            Voucher v = Voucher.builder()
                    .voucherCode(code)
                    .description(request.getDescription())
                    .discountType(request.getDiscountType())
                    .discountValue(request.getDiscountValue())
                    .minOrderAmount(request.getMinOrderAmount())
                    .maxDiscountAmount(request.getMaxDiscountAmount())
                    .validFrom(request.getValidFrom() != null ? request.getValidFrom() : LocalDateTime.now())
                    .validUntil(request.getValidUntil())
                    .usageLimit(request.getUsageLimitPerVoucher())
                    .usageCount(0)
                    .active(true)
                    .hidden(Boolean.TRUE.equals(request.getHidden()))
                    .revealTrigger(Boolean.TRUE.equals(request.getHidden()) ? safeTrigger(request.getRevealTrigger()) : "PUBLIC")
                    .build();
            created.add(voucherRepository.save(v));
        }
        
        return created.stream().map(this::toDto).toList();
    }

    @Transactional
    public void deleteVoucher(Long id) {
        voucherRepository.deleteById(id);
    }

    @Transactional
    public Voucher assignPrecreatedAiReward(User user, Order order) {
        List<Voucher> candidates = voucherRepository.findUnassignedAiRewardCandidates(LocalDateTime.now());
        if (candidates.isEmpty()) {
            // Generate a fresh 10% discount reward voucher on the fly to guarantee they always get rewarded!
            String code = generateUniqueCode("AIPROMO");
            Voucher reward = Voucher.builder()
                    .voucherCode(code)
                    .description("Phần thưởng mua sắm cùng Trợ lý AI (Giảm 10%)")
                    .discountType("PERCENTAGE")
                    .discountValue(java.math.BigDecimal.valueOf(10)) // 10% discount
                    .minOrderAmount(java.math.BigDecimal.valueOf(100000)) // Min order 100k
                    .maxDiscountAmount(java.math.BigDecimal.valueOf(50000)) // Max discount 50k
                    .validFrom(LocalDateTime.now())
                    .validUntil(LocalDateTime.now().plusDays(7)) // Valid for 7 days
                    .usageLimit(1)
                    .usageCount(0)
                    .active(true)
                    .hidden(true)
                    .revealTrigger("AI_ORDER_COMPLETED")
                    .assignedUser(user)
                    .unlockedByOrder(order)
                    .build();
            return voucherRepository.save(reward);
        }
        Voucher reward = candidates.get(0);
        reward.setAssignedUser(user);
        reward.setUnlockedByOrder(order);
        return voucherRepository.save(reward);
    }

    private String generateUniqueCode(String prefix) {
        // Simple random code generation: PREFIX-8chars
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String code = prefix + "-" + random;
        
        // Ensure uniqueness (naive but usually fine for UUID segment)
        while (voucherRepository.findByVoucherCode(code).isPresent()) {
            random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            code = prefix + "-" + random;
        }
        return code;
    }

    private VoucherDto toDto(Voucher v) {
        return VoucherDto.builder()
                .id(v.getId())
                .voucherCode(v.getVoucherCode())
                .description(v.getDescription())
                .discountType(v.getDiscountType())
                .discountValue(v.getDiscountValue())
                .minOrderAmount(v.getMinOrderAmount())
                .maxDiscountAmount(v.getMaxDiscountAmount())
                .validUntil(v.getValidUntil())
                .active(v.getActive())
                .hidden(v.getHidden())
                .revealTrigger(v.getRevealTrigger())
                .assignedUserId(v.getAssignedUser() != null ? v.getAssignedUser().getId() : null)
                .unlockedByOrderId(v.getUnlockedByOrder() != null ? v.getUnlockedByOrder().getId() : null)
                .usageLimitPerVoucher(v.getUsageLimit())
                .usedCount(v.getUsageCount())
                .status(Boolean.TRUE.equals(v.getActive()) ? "ACTIVE" : "INACTIVE")
                .build();
    }

    public VoucherDto toDtoPublic(Voucher voucher) {
        return toDto(voucher);
    }

    private String safeTrigger(String trigger) {
        if (trigger == null || trigger.isBlank()) return "AI_ORDER_COMPLETED";
        return trigger.trim().substring(0, Math.min(trigger.trim().length(), 40));
    }
}
