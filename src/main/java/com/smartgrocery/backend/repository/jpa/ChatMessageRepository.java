package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatSession_IdOrderByCreatedAtAsc(Long chatSessionId);
}
