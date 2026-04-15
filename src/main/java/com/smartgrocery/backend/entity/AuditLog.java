package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="audit_logs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="actor_user_id") private User actor;
    private String actionCode;
    private String targetType;
    private Long targetId;
    @Column(columnDefinition="jsonb") private String payloadJson;
    @CreationTimestamp private LocalDateTime createdAt;
}