package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySession_IdOrderByCreatedAtAsc(Long sessionId);

    List<ChatMessage> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Lấy N tin nhắn gần nhất trong session (cho context window) */
    List<ChatMessage> findTop20BySession_IdOrderByCreatedAtDesc(Long sessionId);

    long countBySession_Id(Long sessionId);

    long countBySession_IdAndFeedbackType(Long sessionId, String feedbackType);

    Optional<ChatMessage> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update ChatMessage m set m.replyStatus = :status, m.replyStartedAt = :startedAt where m.id = :id")
    int updateReplyStatus(Long id, String status, LocalDateTime startedAt);

    @Modifying
    @Query("""
            update ChatMessage m
            set m.replyStatus = :status,
                m.finalReply = :finalReply,
                m.content = :finalReply,
                m.replyCompletedAt = :completedAt,
                m.replyErrorCode = null,
                m.replyErrorMessage = null
            where m.id = :id
            """)
    int updateFinalReplyAndStatus(Long id, String finalReply, String status, LocalDateTime completedAt);

    @Modifying
    @Query("""
            update ChatMessage m
            set m.replyStatus = :status,
                m.finalReply = :fallbackReply,
                m.content = :fallbackReply,
                m.replyCompletedAt = :completedAt,
                m.replyErrorCode = :errorCode,
                m.replyErrorMessage = :errorMessage
            where m.id = :id
            """)
    int updateFallbackReplyAndStatus(
            Long id,
            String fallbackReply,
            String status,
            LocalDateTime completedAt,
            String errorCode,
            String errorMessage
    );
}
