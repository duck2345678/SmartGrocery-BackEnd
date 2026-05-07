package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.*;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication APIs for traditional and Firebase login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Traditional user registration")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Device-Fingerprint", required = false) String deviceFingerprint
    ) {
        return ResponseEntity.ok(authService.register(request, deviceFingerprint));
    }

    @PostMapping("/login")
    @Operation(summary = "Traditional user login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Fingerprint", required = false) String deviceFingerprint
    ) {
        return ResponseEntity.ok(authService.login(request, deviceFingerprint));
    }

    @PostMapping("/firebase-login")
    @Operation(summary = "Token Exchange: Verify Firebase ID Token and return internal JWT")
    public ResponseEntity<AuthResponse> loginWithFirebase(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-Device-Fingerprint", required = false) String deviceFingerprint
    ) {
        String idToken = request.get("idToken");
        if (idToken == null || idToken.isEmpty()) {
            throw new RuntimeException("Firebase ID Token is required");
        }
        return ResponseEntity.ok(authService.loginWithFirebase(idToken, deviceFingerprint));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody TokenRefreshRequest request,
            HttpServletRequest servletRequest,
            @RequestHeader(value = "X-Device-Fingerprint", required = false) String deviceFingerprint
    ) {
        String userAgent = servletRequest.getHeader("User-Agent");
        String ipAddress = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken(), userAgent, ipAddress, deviceFingerprint));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate refresh token")
    public ResponseEntity<Void> logout(
            @RequestBody TokenRefreshRequest request,
            @RequestHeader(value = "X-Device-Fingerprint", required = false) String deviceFingerprint
    ) {
        authService.logout(request.getRefreshToken(), deviceFingerprint);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            throw new RuntimeException("Not authenticated");
        }
        return ResponseEntity.ok(authService.getCurrentUserDto(user.getId()));
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<UserDto> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> updates
    ) {
        if (user == null) {
            throw new RuntimeException("Not authenticated");
        }
        return ResponseEntity.ok(authService.updateUserProfile(user, updates));
    }
}
