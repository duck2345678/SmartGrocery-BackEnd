package com.smartgrocery.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_msg_session_time", columnList = "session_id, created_at"),
        @Index(name = "idx_chat_msg_user", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** USER or ASSISTANT */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** MEMM: Intent AI đã phân tích (CHAT, MEAL_PLAN, NUTRITION, CART_CHECK) */
    @Column(name = "intent_detected", length = 50)
    private String intentDetected;

    /** MEMM: Độ tin cậy của response (0.0 - 1.0) */
    @Column(name = "confidence_score")
    private Float confidenceScore;

    /** MEMM Feedback: HELPFUL, NOT_HELPFUL, null */
    @Column(name = "feedback_type", length = 20)
    private String feedbackType;

    /** Số token tiêu thụ (tracking chi phí) */
    @Column(name = "tokens_used")
    private Integer tokensUsed;

    /** Two-pass AI reply status: PENDING_PASS2, STREAMING, DONE, FALLBACK, FAILED */
    @Column(name = "reply_status", length = 40)
    private String replyStatus;

    @Column(name = "fallback_reply", columnDefinition = "TEXT")
    private String fallbackReply;

    @Column(name = "final_reply", columnDefinition = "TEXT")
    private String finalReply;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validated_action_snapshot", columnDefinition = "jsonb")
    private String validatedActionSnapshot;

    @Column(name = "reply_started_at")
    private LocalDateTime replyStartedAt;

    @Column(name = "reply_completed_at")
    private LocalDateTime replyCompletedAt;

    @Column(name = "reply_error_code", length = 80)
    private String replyErrorCode;

    @Column(name = "reply_error_message", columnDefinition = "TEXT")
    private String replyErrorMessage;
}
