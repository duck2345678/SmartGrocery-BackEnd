package com.smartgrocery.backend.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class SseStreamRegistry {

    private static final long EMITTER_TIMEOUT_MS = 120_000L;
    private static final int MAX_BUFFERED_DELTAS = 200;

    private final com.smartgrocery.backend.repository.jpa.ChatMessageRepository chatMessageRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final Map<Long, StreamState> states = new ConcurrentHashMap<>();

    public SseEmitter register(Long messageId) {
        StreamState state = states.computeIfAbsent(messageId, ignored -> new StreamState());
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        state.emitters.add(emitter);

        emitter.onCompletion(() -> state.emitters.remove(emitter));
        emitter.onTimeout(() -> {
            state.emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> state.emitters.remove(emitter));

        replay(messageId, emitter, state);
        return emitter;
    }

    public void emitDelta(Long messageId, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        StreamState state = states.computeIfAbsent(messageId, ignored -> new StreamState());
        state.deltaBuffer.add(delta);
        trimBuffer(state);
        emit(messageId, "reply_delta", Map.of("text", delta));
    }

    public void emitStatus(Long messageId, String status) {
        if (status == null || status.isEmpty()) return;
        emit(messageId, "status_update", Map.of("status", status));
    }

    public void emitPayload(Long messageId, Object payload) {
        if (payload == null) return;
        emit(messageId, "payload_update", payload);
    }

    public void emitWarning(Long messageId, String code) {
        emit(messageId, "warning", Map.of("code", code));
    }

    public void emitDone(Long messageId, String finalReply, boolean fallback) {
        StreamState state = states.computeIfAbsent(messageId, ignored -> new StreamState());
        state.done = true;
        state.fallback = fallback;
        state.finalReply = finalReply != null ? finalReply : "";
        emit(messageId, "done", Map.of(
                "finalReply", state.finalReply,
                "fallback", fallback
        ));
        state.emitters.forEach(SseEmitter::complete);
        state.emitters.clear();
    }

    private void replay(Long messageId, SseEmitter emitter, StreamState state) {
        String bufferedText = String.join("", state.deltaBuffer);
        if (!bufferedText.isBlank()) {
            send(emitter, "reply_delta", Map.of("text", bufferedText));
        }
        if (state.done) {
            send(emitter, "done", Map.of(
                    "finalReply", state.finalReply != null ? state.finalReply : "",
                    "fallback", state.fallback
            ));
            emitter.complete();
            state.emitters.remove(emitter);
        } else {
            send(emitter, "stream_open", Map.of("messageId", messageId));
        }
    }

    private void emit(Long messageId, String eventName, Object payload) {
        StreamState state = states.get(messageId);
        if (state == null) {
            return;
        }
        for (SseEmitter emitter : new ArrayList<>(state.emitters)) {
            if (!send(emitter, eventName, payload)) {
                state.emitters.remove(emitter);
            }
        }
    }

    private boolean send(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE send failed for event {}: {}", eventName, e.getMessage());
            emitter.completeWithError(e);
            return false;
        }
    }

    private void trimBuffer(StreamState state) {
        while (state.deltaBuffer.size() > MAX_BUFFERED_DELTAS) {
            state.deltaBuffer.remove(0);
        }
    }

    /**
     * Periodically clean up finished stream states that have no active emitters.
     * This prevents the states map from growing indefinitely.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300_000) // Every 5 minutes
    public void cleanup() {
        int initialSize = states.size();
        long now = System.currentTimeMillis();
        long ttlMs = 600_000L; // 10 minutes

        states.entrySet().removeIf(entry -> {
            StreamState state = entry.getValue();
            // Cleanup if:
            // 1. DONE and no active emitters
            // 2. OR state is older than TTL (stuck worker/leak)
            boolean isDoneAndEmpty = state.done && state.emitters.isEmpty();
            boolean isStuck = (now - state.lastUpdatedAt) > ttlMs;

            if (isStuck && !state.done) {
                long ageSeconds = (now - state.lastUpdatedAt) / 1000;
                log.warn("SSE stream state expired by TTL: messageId={}, age={}s. Forcing cleanup and DB fallback.",
                        entry.getKey(), ageSeconds);

                state.emitters.forEach(SseEmitter::complete);

                // Mark DB as FALLBACK to maintain consistency
                transactionTemplate.executeWithoutResult(status ->
                    chatMessageRepository.updateReplyStatus(entry.getKey(), "FALLBACK", LocalDateTime.now())
                );
            }
            return isDoneAndEmpty || isStuck;
        });

        int removedCount = initialSize - states.size();
        if (removedCount > 0) {
            log.info("SSE Registry cleanup: removed {} stream states. Remaining: {}", removedCount, states.size());
        }
    }

    private static class StreamState {
        private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        private final List<String> deltaBuffer = new CopyOnWriteArrayList<>();
        private volatile boolean done;
        private volatile boolean fallback;
        private volatile String finalReply;
        private final long lastUpdatedAt = System.currentTimeMillis();
    }
}
