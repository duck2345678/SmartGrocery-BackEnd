package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.VoucherDto;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserClaimedVoucher;
import com.smartgrocery.backend.entity.Voucher;
import com.smartgrocery.backend.repository.jpa.UserClaimedVoucherRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import com.smartgrocery.backend.repository.jpa.UserVoucherUsageRepository;
import com.smartgrocery.backend.repository.jpa.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private UserClaimedVoucherRepository userClaimedVoucherRepository;

    @Mock
    private VoucherClaimLogService voucherClaimLogService;

    @Mock
    private UserVoucherUsageRepository userVoucherUsageRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private VoucherService voucherService;

    private User user;
    private Voucher voucher;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(10L)
                .email("customer@smartgrocery.vn")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build();

        voucher = Voucher.builder()
                .id(99L)
                .voucherCode("SAVE10")
                .description("Giảm 10k")
                .discountType("FIXED_AMOUNT")
                .discountValue(BigDecimal.valueOf(10000))
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .usageLimit(5)
                .claimCount(0)
                .usageCount(0)
                .active(true)
                .hidden(false)
                .build();
    }

    @Test
    void claimVoucherSuccess() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.of(voucher));
        when(userClaimedVoucherRepository.findByUser_IdAndVoucher_Id(10L, 99L)).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userClaimedVoucherRepository.save(any(UserClaimedVoucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VoucherDto result = voucherService.claimVoucher(user, 99L);

        assertEquals(99L, result.getId());
        assertEquals("SAVE10", result.getVoucherCode());
        assertTrue(result.getClaimed());
        assertFalse(Boolean.TRUE.equals(result.getUsed()));
        assertNotNull(result.getClaimedAt());
        verify(voucherClaimLogService).logClaim(any(User.class), any(Voucher.class), org.mockito.ArgumentMatchers.eq("SUCCESS"), any(), any());
    }

    @Test
    void claimVoucherFailsWhenVoucherMissing() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> voucherService.claimVoucher(user, 99L));

        assertEquals("Voucher không tồn tại", ex.getMessage());
        verify(voucherClaimLogService).logClaimById(10L, 99L, "FAILED", "Voucher không tồn tại");
        verify(voucherRepository, never()).save(any());
    }

    @Test
    void claimVoucherFailsWhenQuotaExceeded() {
        voucher.setClaimCount(5);
        when(voucherRepository.findById(99L)).thenReturn(Optional.of(voucher));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> voucherService.claimVoucher(user, 99L));

        assertEquals("Voucher đã hết lượt nhận", ex.getMessage());
        verify(voucherClaimLogService).logClaimById(10L, 99L, "FAILED", "Voucher đã hết lượt nhận");
        verify(voucherRepository, never()).save(any());
    }

    @Test
    void claimVoucherFailsWhenAlreadyClaimed() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.of(voucher));
        when(userClaimedVoucherRepository.findByUser_IdAndVoucher_Id(10L, 99L)).thenReturn(Optional.of(UserClaimedVoucher.builder().id(1L).build()));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> voucherService.claimVoucher(user, 99L));

        assertEquals("Bạn đã lưu voucher này rồi", ex.getMessage());
        verify(voucherClaimLogService).logClaimById(10L, 99L, "FAILED", "Bạn đã lưu voucher này rồi");
        verify(voucherRepository, never()).save(any());
    }

    @Test
    void claimVoucherSmokeBenchmarkShouldStayUnder200ms() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.of(voucher));
        when(userClaimedVoucherRepository.findByUser_IdAndVoucher_Id(10L, 99L)).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userClaimedVoucherRepository.save(any(UserClaimedVoucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        long startedAt = System.nanoTime();
        voucherService.claimVoucher(user, 99L);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertTrue(elapsedMs < 200, "Expected claimVoucher to finish under 200ms in mocked conditions but took " + elapsedMs + "ms");
    }
}
