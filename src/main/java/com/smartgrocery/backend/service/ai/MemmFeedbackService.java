package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.ChatMessage;
import com.smartgrocery.backend.entity.ChatSession;
import com.smartgrocery.backend.repository.jpa.ChatMessageRepository;
import com.smartgrocery.backend.repository.jpa.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MEMM Feedback Loop Service.
 * Aggregates user feedback to calculate confirmation and satisfaction scores,
 * then adjusts AI behavior in subsequent sessions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemmFeedbackService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    /**
     * Calculates aggregated feedback scores for a user across all their sessions.
     */
    @Transactional(readOnly = true)
    public FeedbackScores calculateUserFeedbackScores(Long userId) {
        // Get all sessions for user
        List<ChatSession> sessions = sessionRepository.findByUser_IdOrderByLastActiveAtDesc(userId);

        if (sessions.isEmpty()) {
            return FeedbackScores.builder()
                    .confirmationScore(0.5f) // Neutral starting point
                    .satisfactionScore(3.0f)  // Neutral satisfaction
                    .totalFeedbackCount(0L)
                    .build();
        }

        // Aggregate feedback across all sessions
        long totalHelpful = 0;
        long totalNotHelpful = 0;
        long totalFeedback = 0;

        for (ChatSession session : sessions) {
            List<ChatMessage> messages = messageRepository.findBySession_IdOrderByCreatedAtAsc(session.getId());
            for (ChatMessage msg : messages) {
                if (msg.getFeedbackType() != null) {
                    totalFeedback++;
                    if ("HELPFUL".equals(msg.getFeedbackType())) {
                        totalHelpful++;
                    } else if ("NOT_HELPFUL".equals(msg.getFeedbackType())) {
                        totalNotHelpful++;
                    }
                }
            }
        }

        // Calculate scores
        float satisfactionScore = totalFeedback > 0
                ? (float) totalHelpful / totalFeedback * 5.0f
                : 3.0f; // Default neutral

        float confirmationScore = totalFeedback > 0
                ? (float) (totalHelpful + totalNotHelpful) / totalFeedback
                : 0.5f; // Default neutral

        return FeedbackScores.builder()
                .confirmationScore(Math.max(0.0f, Math.min(1.0f, confirmationScore)))
                .satisfactionScore(Math.max(0.0f, Math.min(5.0f, satisfactionScore)))
                .totalFeedbackCount(totalFeedback)
                .build();
    }

    /**
     * Determines the AI behavior mode based on satisfaction score.
     */
    public AiBehaviorMode determineBehaviorMode(Float satisfactionScore) {
        if (satisfactionScore == null) {
            return AiBehaviorMode.NORMAL;
        }

        if (satisfactionScore < 3.0f) {
            return AiBehaviorMode.ASK_MORE; // Ask more questions before suggesting
        } else if (satisfactionScore > 4.0f) {
            return AiBehaviorMode.PROACTIVE; // Be more proactive in suggestions
        } else {
            return AiBehaviorMode.NORMAL;
        }
    }

    /**
     * Updates session context with feedback-based behavior adjustments.
     */
    @Transactional
    public void updateSessionBehaviorContext(ChatSession session, FeedbackScores scores) {
        AiBehaviorMode mode = determineBehaviorMode(scores.getSatisfactionScore());

        Map<String, Object> context = new LinkedHashMap<>();
        if (session.getSessionContext() != null && !session.getSessionContext().isBlank()) {
            try {
                Map<?, ?> raw = objectMapper.readValue(session.getSessionContext(), Map.class);
                raw.forEach((key, value) -> context.put(String.valueOf(key), value));
            } catch (Exception e) {
                log.warn("Could not parse session context while applying feedback: {}", e.getMessage());
            }
        }

        context.put("behaviorMode", mode.name().toLowerCase());
        context.put("satisfactionScore", scores.getSatisfactionScore());
        context.put("confirmationScore", scores.getConfirmationScore());
        context.put("lastFeedbackUpdated", LocalDateTime.now().toString());

        try {
            session.setSessionContext(objectMapper.writeValueAsString(context));
        } catch (Exception e) {
            log.warn("Could not serialize feedback session context: {}", e.getMessage());
            session.setSessionContext("{}");
        }
        sessionRepository.save(session);

        log.info("Updated session {} behavior mode to {} based on satisfaction score {}",
                session.getId(), mode, scores.getSatisfactionScore());
    }

    /**
     * Processes feedback and updates user scores/behavior.
     */
    @Transactional
    public FeedbackScores processFeedback(Long userId, Long messageId, String feedbackType) {
        // Update message feedback
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        message.setFeedbackType(feedbackType);
        messageRepository.save(message);

        // Recalculate scores
        FeedbackScores scores = calculateUserFeedbackScores(userId);

        // Update all active sessions with new behavior
        List<ChatSession> activeSessions = sessionRepository.findByUser_IdAndStatus(userId, "ACTIVE");
        for (ChatSession session : activeSessions) {
            updateSessionBehaviorContext(session, scores);
        }

        log.info("Processed feedback for user {}: type={}, new satisfaction={}",
                userId, feedbackType, scores.getSatisfactionScore());

        return scores;
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // DTOs
    // ──────────────────────────────────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    public static class FeedbackScores {
        private Float confirmationScore; // 0.0 - 1.0: how confident AI should be
        private Float satisfactionScore; // 0.0 - 5.0: user satisfaction level
        private Long totalFeedbackCount;
    }

    public enum AiBehaviorMode {
        ASK_MORE,    // Ask more clarifying questions before suggesting
        NORMAL,      // Standard behavior
        PROACTIVE    // Be more proactive in suggestions
    }
}
