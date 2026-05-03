package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.VoucherDto;
import com.smartgrocery.backend.entity.Voucher;
import com.smartgrocery.backend.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
                .build();
    }
}
