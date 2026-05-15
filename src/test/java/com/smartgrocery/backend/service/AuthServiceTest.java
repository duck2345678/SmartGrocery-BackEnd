package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AuthResponse;
import com.smartgrocery.backend.dto.LoginRequest;
import com.smartgrocery.backend.entity.Role;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserSession;
import com.smartgrocery.backend.repository.jpa.RoleRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import com.smartgrocery.backend.repository.jpa.UserSessionRepository;
import com.smartgrocery.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private UserSessionRepository userSessionRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginCreatesSessionWhenNoActiveSessionExists() {
        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        User user = User.builder()
                .id(10L)
                .email("staff@smartgrocery.com")
                .passwordHash("hashed")
                .role(staffRole)
                .status("ACTIVE")
                .build();

        when(userRepository.findByEmail("staff@smartgrocery.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("access-token");
        when(jwtService.hashToken(any())).thenReturn("hash-1");
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.login(new LoginRequest("staff@smartgrocery.com", "secret123"), "device-a");

        assertNotNull(response);
        assertEquals("access-token", response.getToken());
        assertNotNull(response.getRefreshToken());
        verify(userSessionRepository).revokeAllActiveSessionsByUserId(10L);
        ArgumentCaptor<UserSession> sessionCaptor = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(sessionCaptor.capture());
        assertEquals("device-a", sessionCaptor.getValue().getDeviceFingerprint());
        assertEquals(10L, sessionCaptor.getValue().getUser().getId());
        assertEquals(false, sessionCaptor.getValue().isRevoked());
    }

    @Test
    void loginReplacesAnyExistingSessionWithCurrentDevice() {
        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        User user = User.builder()
                .id(10L)
                .email("staff@smartgrocery.com")
                .passwordHash("hashed")
                .role(staffRole)
                .status("ACTIVE")
                .build();

        when(userRepository.findByEmail("staff@smartgrocery.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("access-token");
        when(jwtService.hashToken(any())).thenReturn("hash-1");
        when(userSessionRepository.revokeAllActiveSessionsByUserId(10L)).thenReturn(1);
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.login(new LoginRequest("staff@smartgrocery.com", "secret123"), "device-b");

        assertNotNull(response);
        verify(userSessionRepository).revokeAllActiveSessionsByUserId(10L);
        verify(userSessionRepository).save(any(UserSession.class));
    }

    @Test
    void refreshRejectsFingerprintMismatchAndRevokesAllSessions() {
        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        User user = User.builder().id(10L).email("staff@smartgrocery.com").role(staffRole).status("ACTIVE").build();
        UserSession session = UserSession.builder()
                .id(100L)
                .user(user)
                .refreshTokenHash("hash-1")
                .deviceFingerprint("device-a")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();

        when(jwtService.hashToken("refresh-token-1")).thenReturn("hash-1");
        when(userSessionRepository.findByRefreshTokenHash("hash-1")).thenReturn(Optional.of(session));
        when(userSessionRepository.revokeAllActiveSessionsByUserId(10L)).thenReturn(1);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.refreshToken("refresh-token-1", "UA", "IP", "device-b"));

        assertEquals("Thiết bị đăng nhập không hợp lệ. Vui lòng đăng nhập lại.", ex.getMessage());
        verify(userSessionRepository).revokeAllActiveSessionsByUserId(10L);
        verify(userSessionRepository, never()).save(eq(session));
    }
}
