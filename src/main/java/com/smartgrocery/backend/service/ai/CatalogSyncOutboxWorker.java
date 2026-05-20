package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.CatalogSyncOutbox;
import com.smartgrocery.backend.repository.jpa.CatalogSyncOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogSyncOutboxWorker {

    private final CatalogSyncOutboxRepository outboxRepository;
    private final Neo4jClient neo4jClient;
    private final ObjectMapper objectMapper;

    @Value("${catalog.sync.worker.enabled:true}")
    private boolean workerEnabled;

    @Value("${catalog.sync.worker.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${catalog.sync.worker.delay-ms:15000}")
    public void pollAndSync() {
        if (!workerEnabled) {
            return;
        }
        List<CatalogSyncOutbox> batch = fetchBatch();
        if (batch.isEmpty()) {
            return;
        }
        for (CatalogSyncOutbox outbox : batch) {
            processSingle(outbox);
        }
    }

    @Transactional
    protected List<CatalogSyncOutbox> fetchBatch() {
        return outboxRepository.lockNextBatchForProcessing(LocalDateTime.now(), Math.max(1, batchSize));
    }

    @Transactional
    protected void processSingle(CatalogSyncOutbox outbox) {
        outbox.setStatus("PROCESSING");
        outboxRepository.save(outbox);
        try {
            Map<String, Object> payload = objectMapper.convertValue(outbox.getPayloadJson(), new TypeReference<>() {});
            upsertNeo4j(outbox.getAggregateType(), payload);
            outbox.setStatus("DONE");
            outbox.setLastError(null);
            outbox.setNextRetryAt(null);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            int retries = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
            outbox.setRetryCount(retries);
            outbox.setStatus(retries >= 4 ? "DEAD" : "FAILED");
            outbox.setLastError(e.getMessage());
            outbox.setNextRetryAt(LocalDateTime.now().plusMinutes(backoffMinutes(retries)));
            outboxRepository.save(outbox);
            log.warn("Catalog sync outbox failed id={}, retries={}: {}", outbox.getId(), retries, e.getMessage());
        }
    }

    private int backoffMinutes(int retry) {
        return switch (retry) {
            case 1 -> 1;
            case 2 -> 5;
            case 3 -> 15;
            default -> 60;
        };
    }

    private void upsertNeo4j(String aggregateType, Map<String, Object> payload) {
        if ("INGREDIENT_CANONICAL".equals(aggregateType)) {
            neo4jClient.query("""
                    MERGE (ic:IngredientCanonical {canonicalId: $id})
                    SET ic.code = $code,
                        ic.nameVi = $nameVi,
                        ic.family = $family,
                        ic.defaultDimension = $dimension,
                        ic.avgWeightG = $avgWeightG,
                        ic.avgVolumeMl = $avgVolumeMl,
                        ic.active = $active
                    """)
                    .bindAll(payload)
                    .run();
            return;
        }
        if ("INGREDIENT_ALIAS".equals(aggregateType)) {
            neo4jClient.query("""
                    MERGE (ia:IngredientAlias {aliasNorm: $aliasNorm, lang: $lang})
                    SET ia.aliasRaw = $aliasRaw,
                        ia.confidence = $confidence,
                        ia.active = $active
                    WITH ia
                    MATCH (ic:IngredientCanonical {canonicalId: $canonicalId})
                    MERGE (ia)-[:ALIAS_OF]->(ic)
                    """)
                    .bindAll(payload)
                    .run();
            return;
        }
        if ("UNIT_CANONICAL".equals(aggregateType)) {
            neo4jClient.query("""
                    MERGE (uc:UnitCanonical {unitCode: $unitCode})
                    SET uc.dimension = $dimension,
                        uc.baseUnitCode = $baseUnitCode,
                        uc.factor = $factor,
                        uc.approximate = $approximate,
                        uc.active = $active
                    """)
                    .bindAll(payload)
                    .run();
            return;
        }
        if ("UNIT_ALIAS".equals(aggregateType)) {
            neo4jClient.query("""
                    MERGE (ua:UnitAlias {aliasNorm: $aliasNorm, locale: $locale})
                    SET ua.aliasRaw = $aliasRaw,
                        ua.confidence = $confidence,
                        ua.active = $active
                    WITH ua
                    MATCH (uc:UnitCanonical {unitCode: $unitCode})
                    MERGE (ua)-[:ALIAS_OF]->(uc)
                    """)
                    .bindAll(payload)
                    .run();
            return;
        }
        if ("PRODUCT_INGREDIENT_MATCH".equals(aggregateType)) {
            neo4jClient.query("""
                    MATCH (p:Product {productId: $productId})
                    MATCH (ic:IngredientCanonical {canonicalId: $canonicalId})
                    MERGE (p)-[:MATCHES_INGREDIENT]->(ic)
                    """)
                    .bindAll(payload)
                    .run();
        }
    }
}
