package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.FcmTokenRequest;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.UserDeviceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication APIs for traditional and Firebase login")
@RequiredArgsConstructor
public class UserDeviceController {

    private final UserDeviceRepository userDeviceRepository;

    @Operation(summary = "Lưu FCM token cho user hiện tại")
    @PutMapping("/fcm-token")
    @Transactional(value = "transactionManager")
    public ResponseEntity<Void> registerFcmToken(@AuthenticationPrincipal User user, @RequestBody FcmTokenRequest request) {
        if (user == null) throw new RuntimeException("Unauthorized");
        if (request == null || request.getFcmToken() == null || request.getFcmToken().isBlank()) {
            throw new IllegalArgumentException("Thiếu fcmToken");
        }
        
        String fcmToken = request.getFcmToken().trim();
        user.setFcmToken(fcmToken);

        // Also save/update to user_devices to ensure NotificationService finds it
        java.util.Optional<com.smartgrocery.backend.entity.UserDevice> existingOpt = userDeviceRepository.findByFcmToken(fcmToken);
        if (existingOpt.isPresent()) {
            com.smartgrocery.backend.entity.UserDevice device = existingOpt.get();
            device.setUser(user);
            device.setLastActive(java.time.LocalDateTime.now());
            userDeviceRepository.save(device);
        } else {
            com.smartgrocery.backend.entity.UserDevice device = com.smartgrocery.backend.entity.UserDevice.builder()
                    .user(user)
                    .fcmToken(fcmToken)
                    .deviceType(null)
                    .lastActive(java.time.LocalDateTime.now())
                    .build();
            userDeviceRepository.save(device);
        }

        return ResponseEntity.ok().build();
    }
}
