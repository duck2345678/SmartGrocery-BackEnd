package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.entity.ChatMessage;
import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.ai.AiPass2StreamService;
import com.smartgrocery.backend.service.ai.ChatAssistantService;
import com.smartgrocery.backend.service.ai.ChatAssistantService.*;
import com.smartgrocery.backend.service.ai.SseStreamRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Chat", description = "MEMM-based AI Shopping Assistant")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    private final ChatAssistantService chatAssistantService;
    private final AiPass2StreamService aiPass2StreamService;
    private final SseStreamRegistry sseStreamRegistry;

    @Operation(summary = "Gửi tin nhắn cho AI (MEMM Pipeline)")
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        String message = body.get("message") != null ? body.get("message").toString().trim() : "";
        if (message.isBlank()) {
            throw new IllegalArgumentException("Message is required");
        }
        Long sessionId = parseLongOrNull(body.get("sessionId"), "sessionId");

        try {
            ChatResponse response = chatAssistantService.processChat(userId, sessionId, message);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("AI chat endpoint failed. Returning degraded fallback response.", ex);
            return ResponseEntity.ok(ChatResponse.builder()
                    .sessionId(sessionId)
                    .reply("Mình chưa hoàn tất được danh sách lúc này, nhưng yêu cầu của bạn đã được ghi nhận. Vui lòng thử lại sau ít phút.")
                    .fallbackReply("Mình chưa hoàn tất được danh sách lúc này, nhưng yêu cầu của bạn đã được ghi nhận. Vui lòng thử lại sau ít phút.")
                    .replyStatus(AiPass2StreamService.STATUS_FALLBACK)
                    .recommendedProductIds(List.of())
                    .proposedItems(List.of())
                    .removeVariantIds(List.of())
                    .removeReasons(Map.of())
                    .explanations(Map.of())
                    .uiActions(List.of())
                    .streamUrl(null)
                    .build());
        }
    }

    @Operation(summary = "Lịch sử chat")
    @GetMapping("/chat/history")
    public ResponseEntity<List<ChatHistoryDto>> getChatHistory() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatAssistantService.getChatHistory(userId));
    }

    @Operation(summary = "Stream final AI reply for a chat message")
    @GetMapping("/chat/messages/{messageId}/stream")
    public SseEmitter streamChatMessage(@PathVariable Long messageId) {
        Long userId = SecurityUtils.getCurrentUserId();
        ChatMessage message = aiPass2StreamService.getMessageForStream(messageId, userId);
        SseEmitter emitter = sseStreamRegistry.register(messageId);

        if (AiPass2StreamService.STATUS_DONE.equals(message.getReplyStatus())
                || AiPass2StreamService.STATUS_FALLBACK.equals(message.getReplyStatus())) {
            String finalReply = message.getFinalReply() != null ? message.getFinalReply() : message.getContent();
            sseStreamRegistry.emitDelta(messageId, finalReply);
            sseStreamRegistry.emitDone(
                    messageId,
                    finalReply,
                    AiPass2StreamService.STATUS_FALLBACK.equals(message.getReplyStatus())
            );
        }

        return emitter;
    }

    @Operation(summary = "Gửi feedback cho tin nhắn AI (MEMM Feedback Loop)")
    @PostMapping("/chat/feedback")
    public ResponseEntity<Map<String, String>> submitFeedback(@RequestBody Map<String, Object> body) {
        Long messageId = parseLongLenient(body.get("messageId"));
        String feedbackType = body.get("feedbackType") != null ? body.get("feedbackType").toString().trim() : "";
        if (messageId == null || feedbackType.isBlank()) {
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }

        boolean accepted = chatAssistantService.handleFeedback(messageId, feedbackType);
        if (!accepted) {
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private Long parseLongOrNull(Object value, String fieldName) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
    }

    private Long parseLongLenient(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Operation(summary = "Lấy danh sách AI Nudges")
    @GetMapping("/nudges")
    public ResponseEntity<List<com.smartgrocery.backend.dto.AINudgeDto>> getAiNudges() {
        // Return empty list for now to fix 404 until logic is implemented
        return ResponseEntity.ok(java.util.Collections.emptyList());
    }
}
