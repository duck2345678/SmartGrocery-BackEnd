package com.smartgrocery.backend.service.ai;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationStateManager {

    @Data
    @Builder
    public static class ConversationState {
        private String lastIntent;
        private String scenarioCode;
        private Instant lastUpdated;
    }

    private final Map<String, ConversationState> stateCache = new ConcurrentHashMap<>();
    private static final long STATE_TTL_SECONDS = 300; // 5 minutes

    public void saveState(String sessionId, String intent, String scenarioCode) {
        if (sessionId == null) return;
        stateCache.put(sessionId, ConversationState.builder()
                .lastIntent(intent)
                .scenarioCode(scenarioCode)
                .lastUpdated(Instant.now())
                .build());
    }

    public ConversationState getState(String sessionId) {
        if (sessionId == null) return null;
        ConversationState state = stateCache.get(sessionId);
        if (state == null) return null;
        if (Instant.now().getEpochSecond() - state.getLastUpdated().getEpochSecond() > STATE_TTL_SECONDS) {
            stateCache.remove(sessionId);
            return null;
        }
        return state;
    }

    public void clearState(String sessionId) {
        if (sessionId != null) {
            stateCache.remove(sessionId);
        }
    }
}
