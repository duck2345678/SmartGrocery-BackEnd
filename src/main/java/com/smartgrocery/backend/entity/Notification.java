package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity @Table(name="notifications")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="user_id") @JsonIgnore private User user;
    private String notificationType;
    private String title;
    private String message;
    private Boolean isRead;
    @CreationTimestamp private LocalDateTime createdAt;
}
