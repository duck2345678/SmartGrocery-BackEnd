package com.smartgrocery.backend.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.smartgrocery.backend.dto.*;
import com.smartgrocery.backend.entity.Role;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserSession;
import com.smartgrocery.backend.repository.jpa.RoleRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import com.smartgrocery.backend.repository.jpa.UserSessionRepository;
import com.smartgrocery.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserSessionRepository userSessionRepository;
    private final EmailOtpService emailOtpService;
    private final AccountEmailService accountEmailService;

    // ── Register: tạo PENDING_VERIFY + gửi OTP ───────────────────────────────

    @Transactional("transactionManager")
    public RegistrationPendingResponse register(RegisterRequest request, String deviceFingerprint) {
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone is required");
        }
        String phone = request.getPhone().trim();
        if (!phone.matches("^(\\+84|0)\\d{9,10}$")) {
            throw new IllegalArgumentException("Invalid phone number");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            // Nếu đã tồn tại và PENDING_VERIFY, cho phép gửi lại OTP
            User existing = userRepository.findByEmail(request.getEmail()).orElseThrow();
            if ("PENDING_VERIFY".equals(existing.getStatus())) {
                if (!phone.equals(existing.getPhone()) && userRepository.existsByPhoneAndIdNot(phone, existing.getId())) {
                    throw new IllegalArgumentException("Phone already exists");
                }
                existing.setFullName(request.getFullName());
                existing.setPhone(phone);
                existing.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                userRepository.save(existing);

                String otp = emailOtpService.generateAndSave(request.getEmail(), "EMAIL_VERIFY");
                accountEmailService.sendEmailVerificationOtp(request.getEmail(), existing.getFullName(), otp);
                return RegistrationPendingResponse.builder()
                        .email(request.getEmail())
                        .requiresEmailVerification(true)
                        .expiresInSeconds(600)
                        .message("Email đã được đăng ký nhưng chưa xác nhận. Mã xác nhận mới đã được gửi.")
                        .build();
            }
            throw new RuntimeException("Email này đã được đăng ký.");
        }

        if (userRepository.findByPhone(phone).isPresent()) {
            throw new IllegalArgumentException("Số điện thoại này đã được sử dụng bởi một tài khoản khác.");
        }

        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("CUSTOMER").description("Default Customer Role").build()));

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(phone)
                .role(customerRole)
                .status("PENDING_VERIFY")
                .build();
        userRepository.save(user);

        String otp = emailOtpService.generateAndSave(request.getEmail(), "EMAIL_VERIFY");
        accountEmailService.sendEmailVerificationOtp(request.getEmail(), request.getFullName(), otp);

        return RegistrationPendingResponse.builder()
                .email(request.getEmail())
                .requiresEmailVerification(true)
                .expiresInSeconds(600)
                .message("Đăng ký thành công! Vui lòng kiểm tra email để nhận mã xác nhận.")
                .build();
    }

    // ── Verify email OTP → cấp JWT ───────────────────────────────────────────

    @Transactional("transactionManager")
    public AuthResponse verifyEmail(VerifyEmailRequest request, String deviceFingerprint) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản."));

        if ("ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Email này đã được xác nhận trước đó.");
        }
        if (!"PENDING_VERIFY".equals(user.getStatus())) {
            throw new RuntimeException("Tài khoản không ở trạng thái chờ xác nhận.");
        }

        emailOtpService.verifyAndConsume(request.getEmail(), "EMAIL_VERIFY", request.getOtp());

        user.setStatus("ACTIVE");
        user.setEmailVerifiedAt(LocalDateTime.now());
        userRepository.save(user);

        String jwt = jwtService.generateToken(user);
        String refreshToken = UUID.randomUUID().toString();
        createSession(user, refreshToken, "Unknown", "Unknown", deviceFingerprint);

        return AuthResponse.builder()
                .token(jwt)
                .refreshToken(refreshToken)
                .user(mapToUserDto(user))
                .build();
    }

    // ── Resend email verification ─────────────────────────────────────────────

    @Transactional("transactionManager")
    public void resendEmailVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản."));
        if (!"PENDING_VERIFY".equals(user.getStatus())) {
            throw new RuntimeException("Tài khoản này không cần xác nhận email.");
        }
        String otp = emailOtpService.generateAndSave(email, "EMAIL_VERIFY");
        accountEmailService.sendEmailVerificationOtp(email, user.getFullName(), otp);
    }

    // ── Forgot password: gửi OTP reset ───────────────────────────────────────

    @Transactional("transactionManager")
    public void forgotPassword(String email) {
        // Response chung để tránh leak thông tin user
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!"ACTIVE".equals(user.getStatus())) return;
            try {
                String otp = emailOtpService.generateAndSave(email, "PASSWORD_RESET");
                accountEmailService.sendPasswordResetOtp(email, user.getFullName(), otp);
            } catch (Exception e) {
                log.warn("Forgot password OTP failed for {}: {}", email, e.getMessage());
            }
        });
    }

    // ── Reset password ────────────────────────────────────────────────────────

    @Transactional("transactionManager")
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản."));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Tài khoản không hợp lệ để đặt lại mật khẩu.");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new RuntimeException("Mật khẩu mới phải có ít nhất 6 ký tự.");
        }

        emailOtpService.verifyAndConsume(request.getEmail(), "PASSWORD_RESET", request.getOtp());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Thu hồi tất cả session cũ để bắt buộc đăng nhập lại
        userSessionRepository.revokeAllActiveSessionsByUserId(user.getId());
        log.info("Password reset successful for email={}", request.getEmail());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request, String deviceFingerprint) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email hoặc mật khẩu không đúng."));

        if ("PENDING_VERIFY".equals(user.getStatus())) {
            throw new RuntimeException("PENDING_VERIFY: Tài khoản chưa được xác nhận email. Vui lòng kiểm tra hộp thư của bạn.");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Tài khoản của bạn đã bị khoá. Vui lòng liên hệ hỗ trợ.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Email hoặc mật khẩu không đúng.");
        }

        String jwt = jwtService.generateToken(user);
        String refreshToken = UUID.randomUUID().toString();
        createSession(user, refreshToken, "Unknown", "Unknown", deviceFingerprint);

        return AuthResponse.builder()
                .token(jwt)
                .refreshToken(refreshToken)
                .user(mapToUserDto(user))
                .build();
    }

    // ── Firebase login ────────────────────────────────────────────────────────

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

            if (user.getFirebaseUid() == null) user.setFirebaseUid(firebaseUid);
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

    // ── Token refresh ─────────────────────────────────────────────────────────

    @Transactional("transactionManager")
    public AuthResponse refreshToken(String refreshToken, String userAgent, String ipAddress, String deviceFingerprint) {
        String hash = jwtService.hashToken(refreshToken);
        UserSession session = userSessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (session.isRevoked() || session.getExpiresAt().isBefore(LocalDateTime.now())) {
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

        session.setRevoked(true);
        userSessionRepository.save(session);

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

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional("transactionManager")
    public void logout(String refreshToken, String deviceFingerprint) {
        String hash = jwtService.hashToken(refreshToken);
        String normalizedFingerprint = normalizeFingerprint(deviceFingerprint);
        userSessionRepository.findByRefreshTokenHash(hash).ifPresent(session -> {
            if (normalizedFingerprint != null && session.getDeviceFingerprint() != null
                    && !session.getDeviceFingerprint().equals(normalizedFingerprint)) return;
            session.setRevoked(true);
            userSessionRepository.save(session);
        });
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public UserDto getCurrentUserDto(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToUserDto(user);
    }

    @Transactional("transactionManager")
    public UserDto updateUserProfile(User user, Map<String, String> updates) {
        if (updates.containsKey("fullName")) user.setFullName(updates.get("fullName"));
        if (updates.containsKey("phone")) user.setPhone(updates.get("phone"));
        if (updates.containsKey("avatarUrl")) user.setAvatarUrl(updates.get("avatarUrl"));
        return mapToUserDto(userRepository.save(user));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    @Transactional("transactionManager")
    private void createSession(User user, String refreshToken, String userAgent, String ipAddress, String deviceFingerprint) {
        String normalizedFingerprint = normalizeFingerprint(deviceFingerprint);
        if (normalizedFingerprint == null || normalizedFingerprint.isBlank()) {
            throw new RuntimeException("Thiết bị đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
        }
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
