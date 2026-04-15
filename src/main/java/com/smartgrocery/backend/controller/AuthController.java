package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AuthResponse;
import com.smartgrocery.backend.dto.LoginRequest;
import com.smartgrocery.backend.dto.RegisterRequest;
import com.smartgrocery.backend.dto.TokenRefreshRequest;
import com.smartgrocery.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Traditional user login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/firebase-login")
    @Operation(summary = "Token Exchange: Verify Firebase ID Token and return internal JWT")
    public ResponseEntity<AuthResponse> loginWithFirebase(@RequestBody Map<String, String> request) {
        String idToken = request.get("idToken");
        if (idToken == null || idToken.isEmpty()) {
            throw new RuntimeException("Firebase ID Token is required");
        }
        return ResponseEntity.ok(authService.loginWithFirebase(idToken));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody TokenRefreshRequest request,
            HttpServletRequest servletRequest
    ) {
        String userAgent = servletRequest.getHeader("User-Agent");
        String ipAddress = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken(), userAgent, ipAddress));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate refresh token")
    public ResponseEntity<Void> logout(@RequestBody TokenRefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
