package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    @Query("SELECT s FROM ChatSession s WHERE s.user.id = :userId AND s.deletedAt IS NULL ORDER BY s.updatedAt DESC")
    Page<ChatSession> findActiveByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT s FROM ChatSession s LEFT JOIN FETCH s.messages WHERE s.id = :id AND s.user.id = :userId AND s.deletedAt IS NULL")
    Optional<ChatSession> findActiveByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
