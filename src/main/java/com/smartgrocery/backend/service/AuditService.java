package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AuditLogDto;
import com.smartgrocery.backend.entity.AuditLog;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.AuditLogRepository;
import com.smartgrocery.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class AuditService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    public List<AuditLogDto> getAllLogs() {
        return auditLogRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public void logEvent(Long actorUserId, String actionCode, String targetType, Long targetId, String payloadJson) {
        User actor = null;
        if (actorUserId != null) {
            Optional<User> actorOpt = userRepository.findById(actorUserId);
            actor = actorOpt.orElse(null);
        }

        AuditLog log = AuditLog.builder()
                .actor(actor)
                .actionCode(actionCode)
                .targetType(targetType)
                .targetId(targetId)
                .payloadJson(payloadJson)
                .build();

        auditLogRepository.save(log);
    }

    private AuditLogDto mapToDto(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .actorUserId(log.getActor() != null ? log.getActor().getId() : null)
                .actorName(log.getActor() != null ? log.getActor().getFullName() : "SYSTEM")
                .actionCode(log.getActionCode())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .payloadJson(log.getPayloadJson())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
