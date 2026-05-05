package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.FcmTokenRequest;
import com.smartgrocery.backend.entity.User;
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

    @Operation(summary = "Lưu FCM token cho user hiện tại")
    @PutMapping("/fcm-token")
    @Transactional(value = "transactionManager")
    public ResponseEntity<Void> registerFcmToken(@AuthenticationPrincipal User user, @RequestBody FcmTokenRequest request) {
        if (user == null) throw new RuntimeException("Unauthorized");
        if (request == null || request.getFcmToken() == null || request.getFcmToken().isBlank()) {
            throw new IllegalArgumentException("Thiếu fcmToken");
        }
        user.setFcmToken(request.getFcmToken().trim());
        return ResponseEntity.ok().build();
    }
}
