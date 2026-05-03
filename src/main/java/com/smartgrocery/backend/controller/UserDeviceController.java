package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.RegisterDeviceRequest;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserDevice;
import com.smartgrocery.backend.repository.UserDeviceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/users/me/devices")
@Tag(name = "User Devices", description = "Đăng ký thiết bị nhận push notifications")
@RequiredArgsConstructor
public class UserDeviceController {

    private final UserDeviceRepository userDeviceRepository;

    @Operation(summary = "Đăng ký / cập nhật FCM token cho thiết bị hiện tại")
    @PostMapping
    @Transactional(value = "transactionManager")
    public ResponseEntity<Void> register(@AuthenticationPrincipal User user, @RequestBody RegisterDeviceRequest request) {
        if (user == null) throw new RuntimeException("Unauthorized");
        if (request == null || request.getFcmToken() == null || request.getFcmToken().isBlank()) {
            throw new IllegalArgumentException("Thiếu fcmToken");
        }

        String token = request.getFcmToken().trim();
        String deviceType = request.getDeviceType() != null ? request.getDeviceType().trim() : null;

        UserDevice device = userDeviceRepository.findByFcmToken(token).orElse(null);
        if (device == null) {
            device = UserDevice.builder()
                    .user(user)
                    .fcmToken(token)
                    .deviceType(deviceType)
                    .lastActive(LocalDateTime.now())
                    .build();
        } else {
            device.setUser(user);
            device.setDeviceType(deviceType);
            device.setLastActive(LocalDateTime.now());
        }
        userDeviceRepository.save(device);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Gỡ đăng ký FCM token (logout)")
    @DeleteMapping
    @Transactional(value = "transactionManager")
    public ResponseEntity<Void> unregister(@AuthenticationPrincipal User user, @RequestParam String fcmToken) {
        if (user == null) throw new RuntimeException("Unauthorized");
        if (fcmToken == null || fcmToken.isBlank()) throw new IllegalArgumentException("Thiếu fcmToken");
        userDeviceRepository.findByFcmToken(fcmToken.trim()).ifPresent(d -> {
            if (d.getUser() != null && d.getUser().getId() != null && d.getUser().getId().equals(user.getId())) {
                userDeviceRepository.deleteByFcmToken(fcmToken.trim());
            }
        });
        return ResponseEntity.ok().build();
    }
}
