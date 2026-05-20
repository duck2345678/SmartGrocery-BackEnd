package com.smartgrocery.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.dto.ChatResponseDto;
import com.smartgrocery.backend.entity.ChatMessage;
import com.smartgrocery.backend.entity.ChatSession;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.ChatMessageRepository;
import com.smartgrocery.backend.repository.jpa.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    public Page<ChatSession> getActiveSessions(User user, Pageable pageable) {
        return chatSessionRepository.findActiveByUserId(user.getId(), pageable);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Optional<ChatSession> getSessionDetails(Long sessionId, User user) {
        return chatSessionRepository.findActiveByIdAndUserId(sessionId, user.getId());
    }

    @Transactional(value = "transactionManager")
    public ChatSession createSession(User user, String title, String contextType) {
        String finalTitle = (title == null || title.trim().isEmpty()) ? "Cuộc trò chuyện mới" : title.trim();
        ChatSession session = ChatSession.builder()
                .user(user)
                .title(finalTitle)
                .contextType(contextType)
                .build();
        return chatSessionRepository.save(session);
    }

    @Transactional(value = "transactionManager")
    public ChatSession renameSession(Long sessionId, User user, String newTitle) {
        ChatSession session = chatSessionRepository.findActiveByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc hội thoại hoặc không có quyền truy cập"));
        
        String title = (newTitle == null || newTitle.trim().isEmpty()) ? "Cuộc trò chuyện mới" : newTitle.trim();
        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        return chatSessionRepository.save(session);
    }

    @Transactional(value = "transactionManager")
    public void softDeleteSession(Long sessionId, User user) {
        ChatSession session = chatSessionRepository.findActiveByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc hội thoại hoặc không có quyền truy cập"));
        session.setDeletedAt(LocalDateTime.now());
        chatSessionRepository.save(session);
    }

    @Transactional(value = "transactionManager")
    public ChatMessage saveMessage(Long sessionId, String role, String content, List<ChatResponseDto.ShoppingItem> shoppingItems, Long latencyMs) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc hội thoại với ID: " + sessionId));

        String shoppingItemsJson = null;
        if (shoppingItems != null && !shoppingItems.isEmpty()) {
            try {
                shoppingItemsJson = objectMapper.writeValueAsString(shoppingItems);
            } catch (JsonProcessingException e) {
                log.error("Error serializing shopping items to JSON", e);
            }
        }

        ChatMessage message = ChatMessage.builder()
                .chatSession(session)
                .role(role.toUpperCase())
                .content(content)
                .shoppingItemsJson(shoppingItemsJson)
                .latencyMs(latencyMs)
                .build();

        ChatMessage saved = chatMessageRepository.save(message);

        // Update the session's updatedAt timestamp
        session.setUpdatedAt(LocalDateTime.now());

        return saved;
    }
}
