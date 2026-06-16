package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.Voucher;
import com.smartgrocery.backend.entity.VoucherClaimLog;
import com.smartgrocery.backend.repository.jpa.VoucherClaimLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VoucherClaimLogService {

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final VoucherClaimLogRepository voucherClaimLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${jwt.secret.key}")
    private String logSecret;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logClaim(User user, Voucher voucher, String result, String message, LocalDateTime claimedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user != null ? user.getId() : null);
        payload.put("voucherId", voucher != null ? voucher.getId() : null);
        payload.put("voucherCode", voucher != null ? voucher.getVoucherCode() : null);
        payload.put("result", result);
        payload.put("message", message);
        payload.put("claimedAt", claimedAt != null ? claimedAt.toString() : null);
        persist("CLAIM_VOUCHER", result, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logClaimById(Long userId, Long voucherId, String result, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("voucherId", voucherId);
        payload.put("result", result);
        payload.put("message", message);
        persist("CLAIM_VOUCHER", result, payload);
    }

    private void persist(String action, String result, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            voucherClaimLogRepository.save(VoucherClaimLog.builder()
                    .action(action)
                    .result(result)
                    .encryptedPayload(encrypt(json))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception ignored) {
            // Logging must never block the claim flow.
        }
    }

    private String encrypt(String plainText) throws Exception {
        byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(logSecret.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
        buffer.put(iv);
        buffer.put(encrypted);
        return Base64.getEncoder().encodeToString(buffer.array());
    }
}