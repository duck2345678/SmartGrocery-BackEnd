package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="otp_verifications")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OtpVerification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="user_id") private User user;
    private String email;
    private String phone;
    private String otpCodeHash;
    private String purpose;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
