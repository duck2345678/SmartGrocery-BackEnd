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
    @Operation(summary = "Register new customer (returns pending verification)")
    public ResponseEntity<RegistrationPendingResponse> register(
            @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Device-Fingerprint", required = false) String deviceFingerprint
    ) {
        return ResponseEntity.ok(authService.register(request, deviceFingerprint));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email with OTP and receive JWT tokens")
    public ResponseEntity<AuthResponse> verifyEmail(
            @RequestBody VerifyEmailRequest request,
            @RequestHeader(value = "X-Device-Fingerprint", required = false) String deviceFingerprint
    ) {
        return ResponseEntity.ok(authService.verifyEmail(request, deviceFingerprint));
    }

    @PostMapping("/resend-email-verification")
    @Operation(summary = "Resend email verification OTP")
    public ResponseEntity<Map<String, String>> resendEmailVerification(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email là bắt buộc.");
        }
        authService.resendEmailVerification(email.trim());
        return ResponseEntity.ok(Map.of("message", "Mã xác nhận mới đã được gửi đến email của bạn."));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset OTP via email")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Nếu email tồn tại trong hệ thống, mã xác nhận đã được gửi."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using email OTP")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập lại."));
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
        if (idToken == null || idToken.isEmpty()) throw new RuntimeException("Firebase ID Token is required");
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
        if (user == null) throw new RuntimeException("Not authenticated");
        return ResponseEntity.ok(authService.getCurrentUserDto(user.getId()));
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<UserDto> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> updates
    ) {
        if (user == null) throw new RuntimeException("Not authenticated");
        return ResponseEntity.ok(authService.updateUserProfile(user, updates));
    }
}
