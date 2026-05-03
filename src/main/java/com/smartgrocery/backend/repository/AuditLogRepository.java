package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
           select a from AuditLog a
           where (:actorId is null or a.actor.id = :actorId)
             and (:actionType is null or a.actionType = :actionType)
             and (:entityType is null or a.entityType = :entityType)
             and (:entityId is null or a.entityId = :entityId)
             and (:fromAt is null or a.createdAt >= :fromAt)
             and (:toAt is null or a.createdAt <= :toAt)
           order by a.createdAt desc
           """)
    Page<AuditLog> search(
            @Param("actorId") Long actorId,
            @Param("actionType") String actionType,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt,
            Pageable pageable
    );
}

