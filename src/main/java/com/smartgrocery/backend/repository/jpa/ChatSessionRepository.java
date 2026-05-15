package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findTopByUser_IdAndStatusOrderByLastActiveAtDesc(Long userId, String status);

    List<ChatSession> findByUser_IdOrderByLastActiveAtDesc(Long userId);

    List<ChatSession> findByUser_IdAndStatus(Long userId, String status);

    List<ChatSession> findByLastActiveAtBeforeAndStatus(LocalDateTime before, String status);
}
