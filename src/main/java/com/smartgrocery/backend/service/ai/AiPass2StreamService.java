package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.ChatMessage;
import com.smartgrocery.backend.repository.jpa.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPass2StreamService {

    public static final String STATUS_PENDING_PASS2 = "PENDING_PASS2";
    public static final String STATUS_STREAMING = "STREAMING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FALLBACK = "FALLBACK";

    @Qualifier("aiPass2Executor")
    private final Executor aiPass2Executor;
    private final ChatMessageRepository chatMessageRepository;
    private final SseStreamRegistry streamRegistry;
    private final OpenRouterClient openRouterClient;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public void submitPass2Job(Long aiMessageId, Long userId) {
        try {
            aiPass2Executor.execute(() -> runPass2(aiMessageId, userId));
        } catch (RejectedExecutionException ex) {
            log.warn("AI Pass2 executor rejected message {}: {}", aiMessageId, ex.getMessage());
            markFallback(aiMessageId, "Mình đã chuẩn bị danh sách cho bạn, nhưng phần trả lời AI đang quá tải. Bạn có thể dùng các sản phẩm bên dưới trước nhé.", "AI_PASS2_BUSY", ex);
        }
    }

    public ChatMessage getMessageForStream(Long aiMessageId, Long userId) {
        return chatMessageRepository.findByIdAndUserId(aiMessageId, userId)
                .orElseThrow(() -> new RuntimeException("Chat message not found"));
    }

    private void runPass2(Long aiMessageId, Long userId) {
        ChatMessage message = chatMessageRepository.findByIdAndUserId(aiMessageId, userId).orElse(null);
        if (message == null) {
            log.warn("AI Pass2 skipped, message {} not found for user {}", aiMessageId, userId);
            return;
        }

        markStreaming(aiMessageId);

        try {
            String prompt = buildPass2Prompt(message);
            OpenRouterClient.AiCompletionResult result = openRouterClient
                    .chatCompletion(prompt, List.of(Map.of("role", "user", "content", "Write the final Vietnamese reply now.")))
                    .block();

            String finalReply = result != null && result.isSuccess() ? extractReply(result.getReply()) : "";
            if (finalReply.isBlank()) {
                finalReply = message.getFallbackReply();
            }

            streamChunked(aiMessageId, finalReply);
            markDone(aiMessageId, finalReply);
            streamRegistry.emitDone(aiMessageId, finalReply, false);
        } catch (Exception ex) {
            String fallback = message.getFallbackReply() != null
                    ? message.getFallbackReply()
                    : "Mình đã chuẩn bị danh sách cho bạn, nhưng phần trả lời AI tạm thời chưa hoàn tất.";
            markFallback(aiMessageId, fallback, "AI_PASS2_FALLBACK", ex);
            streamRegistry.emitDelta(aiMessageId, fallback);
            streamRegistry.emitWarning(aiMessageId, "AI_REPLY_FALLBACK");
            streamRegistry.emitDone(aiMessageId, fallback, true);
        }
    }

    private String buildPass2Prompt(ChatMessage message) {
        return """
                You are SmartGrocery's Vietnamese shopping assistant.

                Write the final user-facing reply based only on this backend-validated snapshot.
                Do not invent product IDs, stock, prices, or products.
                Do not mention internal JSON, backend guard, snapshot, pass 1, pass 2, or system design.
                If removedItems exist, briefly explain that some requested items were unavailable.
                Return JSON only: {"reply":"..."}

                VALIDATED_SNAPSHOT:
                %s
                """.formatted(message.getValidatedActionSnapshot() != null ? message.getValidatedActionSnapshot() : "{}");
    }

    private String extractReply(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            return root.path("reply").asText(raw).trim();
        } catch (Exception ignored) {
            return raw.trim();
        }
    }

    private void streamChunked(Long aiMessageId, String finalReply) {
        if (finalReply == null || finalReply.isBlank()) {
            return;
        }
        int chunkSize = 80;
        for (int i = 0; i < finalReply.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, finalReply.length());
            streamRegistry.emitDelta(aiMessageId, finalReply.substring(i, end));
        }
    }

    private void markStreaming(Long aiMessageId) {
        transactionTemplate.executeWithoutResult(status ->
                chatMessageRepository.updateReplyStatus(aiMessageId, STATUS_STREAMING, LocalDateTime.now()));
    }

    private void markDone(Long aiMessageId, String finalReply) {
        transactionTemplate.executeWithoutResult(status ->
                chatMessageRepository.updateFinalReplyAndStatus(aiMessageId, finalReply, STATUS_DONE, LocalDateTime.now()));
    }

    private void markFallback(Long aiMessageId, String fallbackReply, String errorCode, Exception ex) {
        transactionTemplate.executeWithoutResult(status ->
                chatMessageRepository.updateFallbackReplyAndStatus(
                        aiMessageId,
                        fallbackReply,
                        STATUS_FALLBACK,
                        LocalDateTime.now(),
                        errorCode,
                        ex != null ? ex.getMessage() : null
                ));
    }
}
