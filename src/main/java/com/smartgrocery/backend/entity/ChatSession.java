package com.smartgrocery.backend.entity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity @Table(name="chat_sessions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne @JoinColumn(name="user_id") private User user;
    private String title;
    @CreationTimestamp private LocalDateTime createdAt;
}
