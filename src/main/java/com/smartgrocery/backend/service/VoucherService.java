package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.VoucherDto;
import com.smartgrocery.backend.dto.VoucherGenerationRequest;
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
                    .build();
            created.add(voucherRepository.save(v));
        }
        
        return created.stream().map(this::toDto).toList();
    }

    @Transactional
    public void deleteVoucher(Long id) {
        voucherRepository.deleteById(id);
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
                .build();
    }
}
