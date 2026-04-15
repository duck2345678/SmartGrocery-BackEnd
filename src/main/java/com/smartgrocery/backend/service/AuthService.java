package com.smartgrocery.backend.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.smartgrocery.backend.dto.AuthResponse;
import com.smartgrocery.backend.dto.LoginRequest;
import com.smartgrocery.backend.dto.RegisterRequest;
import com.smartgrocery.backend.dto.UserDto;
import com.smartgrocery.backend.entity.Role;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.RoleRepository;
import com.smartgrocery.backend.repository.UserRepository;
import com.smartgrocery.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional("transactionManager")
    public AuthResponse register(RegisterRequest request) {
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
        return AuthResponse.builder()
                .token(jwt)
                .user(mapToUserDto(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }

        String jwt = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(jwt)
                .user(mapToUserDto(user))
                .build();
    }

    @Transactional("transactionManager")
    public AuthResponse loginWithFirebase(String idToken) {
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

            return AuthResponse.builder()
                    .token(jwt)
                    .user(mapToUserDto(user))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Firebase token verification failed: " + e.getMessage());
        }
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
