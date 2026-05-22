package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.OtpVerification;
import com.smartgrocery.backend.repository.jpa.OtpVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS_PER_HOUR = 5;

    @Value("${app.auth.otp.expiry-minutes:10}")
    private int expiryMinutes;

    @Value("${app.auth.otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    @Value("${app.auth.otp.server-secret:smartgrocery-otp-secret}")
    private String serverSecret;

    private final OtpVerificationRepository otpRepo;

    /**
     * Sinh OTP 6 số, lưu hash vào DB và trả về OTP gốc để gửi email.
     */
    @Transactional("transactionManager")
    public String generateAndSave(String email, String purpose) {
        // Rate limit: tối đa 5 lần gửi mỗi giờ
        long sentLastHour = otpRepo.countSentSince(email, purpose, LocalDateTime.now().minusHours(1));
        if (sentLastHour >= MAX_SENDS_PER_HOUR) {
            throw new RuntimeException("Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau 1 giờ.");
        }

        // Resend cooldown
        otpRepo.findLatestActive(email, purpose, LocalDateTime.now()).ifPresent(existing -> {
            if (existing.getLastSentAt() != null) {
                long secondsSinceLastSend = java.time.Duration.between(existing.getLastSentAt(), LocalDateTime.now()).getSeconds();
                if (secondsSinceLastSend < resendCooldownSeconds) {
                    long wait = resendCooldownSeconds - secondsSinceLastSend;
                    throw new RuntimeException("Vui lòng chờ " + wait + " giây trước khi gửi lại mã.");
                }
            }
        });

        // Huỷ OTP cũ
        otpRepo.cancelAllPending(email, purpose);

        // Sinh OTP
        String otp = generateOtp();
        String hash = hashOtp(otp, email);
        LocalDateTime now = LocalDateTime.now();

        OtpVerification record = OtpVerification.builder()
                .email(email)
                .otpCodeHash(hash)
                .purpose(purpose)
                .status("PENDING")
                .expiresAt(now.plusMinutes(expiryMinutes))
                .createdAt(now)
                .lastSentAt(now)
                .attemptCount(0)
                .build();
        otpRepo.save(record);

        log.info("OTP generated for email={} purpose={}", email, purpose);
        return otp;
    }

    /**
     * Xác thực OTP. Trả về true nếu hợp lệ và đánh dấu CONSUMED.
     * Throw exception nếu sai/hết hạn/quá số lần thử.
     */
    @Transactional("transactionManager")
    public void verifyAndConsume(String email, String purpose, String otp) {
        OtpVerification record = otpRepo.findLatestActive(email, purpose, LocalDateTime.now())
                .orElseThrow(() -> new RuntimeException("Mã xác nhận không hợp lệ hoặc đã hết hạn."));

        // Kiểm tra số lần thử
        if (record.getAttemptCount() >= MAX_ATTEMPTS) {
            record.setStatus("LOCKED");
            otpRepo.save(record);
            throw new RuntimeException("Mã đã bị khoá do nhập sai quá " + MAX_ATTEMPTS + " lần. Vui lòng yêu cầu mã mới.");
        }

        String expectedHash = hashOtp(otp, email);
        if (!expectedHash.equals(record.getOtpCodeHash())) {
            record.setAttemptCount(record.getAttemptCount() + 1);
            otpRepo.save(record);
            int remaining = MAX_ATTEMPTS - record.getAttemptCount();
            throw new RuntimeException("Mã xác nhận không đúng. Còn " + remaining + " lần thử.");
        }

        // Đánh dấu consumed
        record.setStatus("CONSUMED");
        record.setConsumedAt(LocalDateTime.now());
        otpRepo.save(record);
        log.info("OTP verified and consumed for email={} purpose={}", email, purpose);
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int num = 100000 + random.nextInt(900000);
        return String.valueOf(num);
    }

    private String hashOtp(String otp, String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = otp + ":" + email.toLowerCase() + ":" + serverSecret;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Lỗi hệ thống: không thể hash OTP", e);
        }
    }
}
