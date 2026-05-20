package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.CatalogSyncOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CatalogSyncOutboxRepository extends JpaRepository<CatalogSyncOutbox, Long> {

    @Query(value = """
            SELECT *
            FROM catalog_sync_outbox
            WHERE status IN ('PENDING', 'FAILED')
              AND (next_retry_at IS NULL OR next_retry_at <= :now)
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<CatalogSyncOutbox> lockNextBatchForProcessing(@Param("now") LocalDateTime now, @Param("limit") int limit);

    long countByStatus(String status);

    @Query("SELECT o.aggregateId FROM CatalogSyncOutbox o WHERE o.aggregateType = :type")
    List<Long> findAggregateIdsByAggregateType(@Param("type") String type);
}

