package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.RegisterDeviceRequest;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserDevice;
import com.smartgrocery.backend.repository.jpa.UserDeviceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/users/me/devices")
@Tag(name = "User Devices", description = "APIs for managing user push devices")
@RequiredArgsConstructor
public class MeDeviceController {

    private final UserDeviceRepository userDeviceRepository;

    @Operation(summary = "Đăng ký thiết bị nhận push")
    @PostMapping
    @Transactional(value = "transactionManager")
    public ResponseEntity<Void> registerDevice(
            @AuthenticationPrincipal User user,
            @RequestBody RegisterDeviceRequest request
    ) {
        if (user == null) throw new RuntimeException("Unauthorized");
        if (request == null || request.getFcmToken() == null || request.getFcmToken().isBlank()) {
            throw new IllegalArgumentException("Thiếu fcmToken");
        }

        String fcmToken = request.getFcmToken().trim();
        String deviceType = request.getDeviceType() != null ? request.getDeviceType().trim() : null;

        Optional<UserDevice> existingOpt = userDeviceRepository.findByFcmToken(fcmToken);
        if (existingOpt.isPresent()) {
            UserDevice device = existingOpt.get();
            device.setUser(user);
            device.setDeviceType(deviceType);
            device.setLastActive(LocalDateTime.now());
            userDeviceRepository.save(device);
        } else {
            UserDevice device = UserDevice.builder()
                    .user(user)
                    .fcmToken(fcmToken)
                    .deviceType(deviceType)
                    .lastActive(LocalDateTime.now())
                    .build();
            userDeviceRepository.save(device);
        }

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Hủy đăng ký thiết bị nhận push")
    @DeleteMapping
    @Transactional(value = "transactionManager")
    public ResponseEntity<Void> unregisterDevice(
            @AuthenticationPrincipal User user,
            @RequestParam("fcmToken") String fcmToken
    ) {
        if (user == null) throw new RuntimeException("Unauthorized");
        if (fcmToken == null || fcmToken.isBlank()) {
            throw new IllegalArgumentException("Thiếu fcmToken");
        }

        userDeviceRepository.deleteByFcmToken(fcmToken.trim());
        return ResponseEntity.ok().build();
    }
}
