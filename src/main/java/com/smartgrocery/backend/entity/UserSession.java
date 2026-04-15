package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="user_sessions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="user_id") private User user;
    private String refreshTokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}