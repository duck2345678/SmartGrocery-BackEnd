package com.smartgrocery.backend.service.ai;


import com.smartgrocery.backend.dto.ChatResponsePayload;
import com.smartgrocery.backend.entity.ChatMessage;
import com.smartgrocery.backend.repository.jpa.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrchestrationService {

    public static final String STATUS_PENDING_ORCHESTRATION = "PENDING_ORCHESTRATION";
    public static final String STATUS_ANALYZING = "ANALYZING";
    public static final String STATUS_GUARDING = "GUARDING";
    public static final String STATUS_STREAMING = "STREAMING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FALLBACK = "FALLBACK";

    @Qualifier("aiPass2Executor")
    private final Executor aiPass2Executor;
    private final ChatMessageRepository chatMessageRepository;
    private final SseStreamRegistry streamRegistry;
    private final OpenRouterClient openRouterClient;
    private final TransactionTemplate transactionTemplate;
    private final ChatAssistantService chatAssistantService;


    public void orchestrate(Long aiMessageId, Long userId, String userMessage) {
        try {
            aiPass2Executor.execute(() -> runPipeline(aiMessageId, userId, userMessage));
        } catch (RejectedExecutionException ex) {
            log.warn("AI orchestration rejected message {}: {}", aiMessageId, ex.getMessage());
            markFallback(aiMessageId, "Hệ thống đang quá tải, vui lòng thử lại sau.", "AI_BUSY", ex);
        }
    }

    private void runPipeline(Long aiMessageId, Long userId, String userMessage) {
        ChatMessage message = chatMessageRepository.findByIdAndUserId(aiMessageId, userId).orElse(null);
        if (message == null) return;

        try {
            // Stage 1: Analysis & Pass 1
            streamRegistry.emitStatus(aiMessageId, "Đang phân tích yêu cầu...");
            updateStatus(aiMessageId, STATUS_ANALYZING);
            
            ChatResponsePayload payload = chatAssistantService.orchestratePass1(userId, message.getSession().getId(), userMessage);
            
            // Stage 2: Guardrails
            streamRegistry.emitStatus(aiMessageId, "Đang kiểm tra an toàn thực phẩm...");
            updateStatus(aiMessageId, STATUS_GUARDING);
            chatAssistantService.applyGuardrails(payload, userId, userMessage);

            // Stage 3: Persist & Emit Payload
            saveSnapshot(aiMessageId, payload, userMessage);
            streamRegistry.emitPayload(aiMessageId, payload);

            
            // Stage 4: Pass 2 (Streaming Reply)
            streamRegistry.emitStatus(aiMessageId, "Đang soạn câu trả lời...");
            updateStatus(aiMessageId, STATUS_STREAMING);
            
            StringBuilder fullReply = new StringBuilder();
            String pass2Prompt = buildPass2Prompt(userMessage, payload);
            
            openRouterClient.streamChatCompletion(pass2Prompt, List.of(), null)
                    .buffer(java.time.Duration.ofMillis(100))
                    .filter(list -> !list.isEmpty())
                    .map(list -> String.join("", list))
                    .doOnNext(delta -> {
                        fullReply.append(delta);
                        streamRegistry.emitDelta(aiMessageId, delta);
                    })
                    .doOnComplete(() -> {
                        String finalReply = fullReply.toString();
                        markDone(aiMessageId, finalReply);
                        streamRegistry.emitDone(aiMessageId, finalReply, false);
                    })
                    .doOnError(ex -> {
                        log.error("Pass 2 streaming failed", ex);
                        handleFallback(aiMessageId, payload, ex);
                    })
                    .subscribe();


        } catch (Exception ex) {
            log.error("Orchestration pipeline failed", ex);
            handleFallback(aiMessageId, null, ex);
        }
    }

    private String buildPass2Prompt(String userMessage, ChatResponsePayload payload) {
        return """
                You are SmartGrocery's Vietnamese shopping assistant.
                User said: "%s"
                
                Write the final user-facing reply based only on this backend-validated snapshot.
                Do not invent product IDs, stock, prices, or products.
                If removedItems exist, briefly explain that some requested items were unavailable.
                Return text only, no JSON.
                
                VALIDATED_SNAPSHOT:
                %s
                """.formatted(userMessage, chatAssistantService.toJson(payload));
    }

    private void saveSnapshot(Long aiMessageId, ChatResponsePayload payload, String userMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            chatMessageRepository.findById(aiMessageId).ifPresent(msg -> {
                msg.setIntentDetected(payload.getIntentDetected());
                msg.setConfidenceScore(payload.getTrustScore() != null ? payload.getTrustScore() / 100f : null);
                msg.setValidatedActionSnapshot(chatAssistantService.toJson(payload));
                chatMessageRepository.save(msg);
            });
        });
    }

    private void handleFallback(Long aiMessageId, ChatResponsePayload payload, Throwable ex) {
        String fallback = "Mình đã ghi nhận yêu cầu, nhưng phần giải thích AI đang gặp sự cố nhỏ. Bạn hãy xem danh sách sản phẩm bên dưới nhé.";
        markFallback(aiMessageId, fallback, "ORCHESTRATION_ERROR", ex);
        streamRegistry.emitDelta(aiMessageId, fallback);
        streamRegistry.emitDone(aiMessageId, fallback, true);
    }

    public ChatMessage getMessageForStream(Long messageId, Long userId) {
        return chatMessageRepository.findByIdAndUserId(messageId, userId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
    }

    private void updateStatus(Long aiMessageId, String status) {
        transactionTemplate.executeWithoutResult(s ->
                chatMessageRepository.updateReplyStatus(aiMessageId, status, LocalDateTime.now()));
    }

    private void markDone(Long aiMessageId, String finalReply) {
        transactionTemplate.executeWithoutResult(status ->
                chatMessageRepository.updateFinalReplyAndStatus(aiMessageId, finalReply, STATUS_DONE, LocalDateTime.now()));
    }

    private void markFallback(Long aiMessageId, String fallbackReply, String errorCode, Throwable ex) {
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
