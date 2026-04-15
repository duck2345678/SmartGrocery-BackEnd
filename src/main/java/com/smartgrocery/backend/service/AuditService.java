package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AuditLogDto;
import com.smartgrocery.backend.entity.AuditLog;
import com.smartgrocery.backend.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class AuditService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    public List<AuditLogDto> getAllLogs() {
        return auditLogRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
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
