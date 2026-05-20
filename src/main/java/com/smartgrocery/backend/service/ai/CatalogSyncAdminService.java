package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.repository.jpa.CatalogSyncOutboxRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CatalogSyncAdminService {

    @PersistenceContext
    private EntityManager entityManager;

    private final CatalogSyncOutboxRepository outboxRepository;

    @Transactional
    public int requeueDeadEvents() {
        int updated = entityManager.createNativeQuery("""
                UPDATE catalog_sync_outbox
                SET status = 'PENDING',
                    next_retry_at = :now,
                    updated_at = :now
                WHERE status = 'DEAD'
                """)
                .setParameter("now", LocalDateTime.now())
                .executeUpdate();
        return updated;
    }

    public Map<String, Long> getQueueStats() {
        return Map.of(
                "pending", outboxRepository.countByStatus("PENDING"),
                "failed", outboxRepository.countByStatus("FAILED"),
                "dead", outboxRepository.countByStatus("DEAD"),
                "done", outboxRepository.countByStatus("DONE")
        );
    }
}
