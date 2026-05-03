package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.dto.AuditLogDto;
import com.smartgrocery.backend.entity.AuditLog;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.AuditLogRepository;
import com.smartgrocery.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void log(User actor, String actionType, String entityType, Long entityId, String reason, JsonNode beforeState, JsonNode afterState) {
        if (actor == null) throw new IllegalArgumentException("Thiếu actor");
        if (actionType == null || actionType.isBlank()) throw new IllegalArgumentException("Thiếu actionType");
        if (entityType == null || entityType.isBlank()) throw new IllegalArgumentException("Thiếu entityType");
        if (entityId == null) throw new IllegalArgumentException("Thiếu entityId");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Thiếu reason");

        AuditLog log = AuditLog.builder()
                .actor(actor)
                .actionType(actionType.trim().toUpperCase())
                .entityType(entityType.trim().toUpperCase())
                .entityId(entityId)
                .reason(reason.trim())
                .beforeState(beforeState)
                .afterState(afterState)
                .build();
        auditLogRepository.save(log);
    }

    @Transactional
    public void logEvent(Long actorId, String actionType, String entityType, Long entityId, String payloadJson) {
        if (actorId == null) return;
        if (entityId == null) return;
        User actor = userRepository.findById(actorId).orElse(null);
        if (actor == null) return;

        JsonNode payloadNode = null;
        if (payloadJson != null && !payloadJson.isBlank()) {
            try {
                payloadNode = objectMapper.readTree(payloadJson);
            } catch (Exception ignored) {
                payloadNode = null;
            }
        }

        log(actor, actionType, entityType, entityId, "SECURITY_EVENT", null, payloadNode);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> search(Long actorId, String actionType, String entityType, Long entityId, LocalDateTime fromAt, LocalDateTime toAt, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        return auditLogRepository.search(actorId, normalize(actionType), normalize(entityType), entityId, fromAt, toAt, PageRequest.of(safePage, safeSize))
                .map(this::toDto);
    }

    private String normalize(String v) {
        if (v == null || v.isBlank()) return null;
        return v.trim().toUpperCase();
    }

    private AuditLogDto toDto(AuditLog a) {
        return AuditLogDto.builder()
                .id(a.getId())
                .actorId(a.getActor() != null ? a.getActor().getId() : null)
                .actorName(a.getActor() != null ? a.getActor().getFullName() : null)
                .actionType(a.getActionType())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .reason(a.getReason())
                .beforeState(a.getBeforeState())
                .afterState(a.getAfterState())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
