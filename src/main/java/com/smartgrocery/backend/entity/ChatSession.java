package com.smartgrocery.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_sessions", indexes = {
        @Index(name = "idx_chat_session_user", columnList = "user_id"),
        @Index(name = "idx_chat_session_active", columnList = "user_id, last_active_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_active_at")
    @Builder.Default
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    /**
     * MEMM: Trạng thái ngữ cảnh phiên chat (JSONB).
     * Lưu: motivation profile, interaction history summary, adaptive tone level, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "session_context", columnDefinition = "jsonb")
    private String sessionContext;

    /**
     * MEMM: Điểm satisfaction tổng hợp (0.0 - 5.0)
     * Được cập nhật dựa trên feedback loop.
     */
    @Column(name = "satisfaction_score")
    private Float satisfactionScore;

    /**
     * MEMM: Số lượt tương tác trong phiên.
     * Dùng để trigger satisfaction prompt (mỗi 5 lượt).
     */
    @Column(name = "interaction_count")
    @Builder.Default
    private Integer interactionCount = 0;

    @Column(name = "status")
    @Builder.Default
    private String status = "ACTIVE";
}
