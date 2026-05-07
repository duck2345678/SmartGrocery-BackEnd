package com.smartgrocery.backend.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.smartgrocery.backend.dto.AuthResponse;
import com.smartgrocery.backend.dto.LoginRequest;
import com.smartgrocery.backend.dto.RegisterRequest;
import com.smartgrocery.backend.dto.UserDto;
import com.smartgrocery.backend.entity.Role;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserSession;
import com.smartgrocery.backend.repository.RoleRepository;
import com.smartgrocery.backend.repository.UserRepository;
import com.smartgrocery.backend.repository.UserSessionRepository;
import com.smartgrocery.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserSessionRepository userSessionRepository;

    @Transactional("transactionManager")
    public AuthResponse register(RegisterRequest request, String deviceFingerprint) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("CUSTOMER").description("Default Customer Role").build()));

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(customerRole)
                .status("ACTIVE")
                .build();

        user = userRepository.save(user);

        String jwt = jwtService.generateToken(user);
        String refreshToken = UUID.randomUUID().toString();
        createSession(user, refreshToken, "Unknown", "Unknown", deviceFingerprint);

        return AuthResponse.builder()
                .token(jwt)
                .refreshToken(refreshToken)
                .user(mapToUserDto(user))
                .build();
    }

    public AuthResponse login(LoginRequest request, String deviceFingerprint) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }

        String jwt = jwtService.generateToken(user);
        String refreshToken = UUID.randomUUID().toString();
        // In a real scenario, userAgent and ipAddress would be passed from the Controller
        createSession(user, refreshToken, "Unknown", "Unknown", deviceFingerprint);

        return AuthResponse.builder()
                .token(jwt)
                .refreshToken(refreshToken)
                .user(mapToUserDto(user))
                .build();
    }

    @Transactional("transactionManager")
    public AuthResponse loginWithFirebase(String idToken, String deviceFingerprint) {
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String email = decodedToken.getEmail();
            String firebaseUid = decodedToken.getUid();
            String name = (String) decodedToken.getClaims().get("name");
            String picture = (String) decodedToken.getClaims().get("picture");

            User user = userRepository.findByFirebaseUid(firebaseUid)
                    .or(() -> userRepository.findByEmail(email))
                    .orElseGet(() -> {
                        Role customerRole = roleRepository.findByName("CUSTOMER")
                                .orElseGet(() -> roleRepository.save(Role.builder()
                                        .name("CUSTOMER")
                                        .description("Default Customer Role")
                                        .build()));
                        
                        return User.builder()
                                .email(email)
                                .firebaseUid(firebaseUid)
                                .fullName(name != null ? name : "User " + firebaseUid.substring(0, 5))
                                .avatarUrl(picture)
                                .role(customerRole)
                                .status("ACTIVE")
                                .build();
                    });

            // Update firebaseUid if matched by email
            if (user.getFirebaseUid() == null) {
                user.setFirebaseUid(firebaseUid);
            }
            
            user = userRepository.save(user);
            String jwt = jwtService.generateToken(user);
            String refreshToken = UUID.randomUUID().toString();
            createSession(user, refreshToken, "Unknown", "Unknown", deviceFingerprint);

            return AuthResponse.builder()
                    .token(jwt)
                    .refreshToken(refreshToken)
                    .user(mapToUserDto(user))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Firebase token verification failed: " + e.getMessage());
        }
    }

    @Transactional("transactionManager")
    public AuthResponse refreshToken(String refreshToken, String userAgent, String ipAddress, String deviceFingerprint) {
        String hash = jwtService.hashToken(refreshToken);
        UserSession session = userSessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (session.isRevoked() || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            // Anomalous Reuse Detection: If someone uses a revoked token, revoke ALL sessions for that user!
            if (session.isRevoked()) {
                userSessionRepository.revokeAllActiveSessionsByUserId(session.getUser().getId());
                throw new RuntimeException("Refresh token was previously revoked. All sessions invalidated for security.");
            }
            throw new RuntimeException("Refresh token expired");
        }

        String normalizedFingerprint = normalizeFingerprint(deviceFingerprint);
        if (session.getDeviceFingerprint() == null || !session.getDeviceFingerprint().equals(normalizedFingerprint)) {
            userSessionRepository.revokeAllActiveSessionsByUserId(session.getUser().getId());
            throw new RuntimeException("Thiết bị đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
        }

        // Rotate: Revoke current session
        session.setRevoked(true);
        userSessionRepository.save(session);

        // Generate new pair
        User user = session.getUser();
        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = UUID.randomUUID().toString();

        createSession(user, newRefreshToken, userAgent, ipAddress, normalizedFingerprint);

        return AuthResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .user(mapToUserDto(user))
                .build();
    }

    @Transactional("transactionManager")
    public void logout(String refreshToken, String deviceFingerprint) {
        String hash = jwtService.hashToken(refreshToken);
        String normalizedFingerprint = normalizeFingerprint(deviceFingerprint);
        userSessionRepository.findByRefreshTokenHash(hash).ifPresent(session -> {
            if (normalizedFingerprint != null && session.getDeviceFingerprint() != null && !session.getDeviceFingerprint().equals(normalizedFingerprint)) {
                return;
            }
            session.setRevoked(true);
            userSessionRepository.save(session);
        });
    }

    @Transactional("transactionManager")
    private void createSession(User user, String refreshToken, String userAgent, String ipAddress, String deviceFingerprint) {
        String normalizedFingerprint = normalizeFingerprint(deviceFingerprint);
        if (normalizedFingerprint == null || normalizedFingerprint.isBlank()) {
            throw new RuntimeException("Thiết bị đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
        }

        // Single-device policy: any new login takes over the account and invalidates prior sessions.
        userSessionRepository.revokeAllActiveSessionsByUserId(user.getId());

        UserSession session = UserSession.builder()
                .user(user)
                .refreshTokenHash(jwtService.hashToken(refreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .deviceFingerprint(normalizedFingerprint)
                .lastUsedAt(LocalDateTime.now())
                .revoked(false)
                .build();
        userSessionRepository.save(session);
    }

    private String normalizeFingerprint(String deviceFingerprint) {
        return deviceFingerprint == null ? null : deviceFingerprint.trim();
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public UserDto getCurrentUserDto(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToUserDto(user);
    }

    @Transactional("transactionManager")
    public UserDto updateUserProfile(User user, Map<String, String> updates) {
        if (updates.containsKey("fullName")) {
            user.setFullName(updates.get("fullName"));
        }
        if (updates.containsKey("phone")) {
            user.setPhone(updates.get("phone"));
        }
        if (updates.containsKey("avatarUrl")) {
            user.setAvatarUrl(updates.get("avatarUrl"));
        }
        return mapToUserDto(userRepository.save(user));
    }

    private UserDto mapToUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .roleName(user.getRole().getName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .build();
    }
}
