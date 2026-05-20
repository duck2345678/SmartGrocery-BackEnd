package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.CatalogSyncOutbox;
import com.smartgrocery.backend.repository.jpa.CatalogSyncOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogSyncOutboxService {

    private final CatalogSyncOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public java.util.List<Long> getExistingAggregateIds(String type) {
        return outboxRepository.findAggregateIdsByAggregateType(type);
    }

    public record ProductIngredientMatchDto(Long mealIngredientId, Long productId, Long canonicalId) {}

    @Transactional
    public void enqueueMatches(java.util.List<ProductIngredientMatchDto> matches) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        java.util.List<CatalogSyncOutbox> outboxes = new java.util.ArrayList<>();
        for (ProductIngredientMatchDto match : matches) {
            try {
                JsonNode json = objectMapper.valueToTree(Map.of(
                        "productId", match.productId(),
                        "canonicalId", match.canonicalId()
                ));
                outboxes.add(CatalogSyncOutbox.builder()
                        .aggregateType("PRODUCT_INGREDIENT_MATCH")
                        .aggregateId(match.mealIngredientId())
                        .eventType("UPSERT")
                        .payloadJson(json)
                        .status("PENDING")
                        .retryCount(0)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to serialize outbox payload for product-ingredient match: {}", e.getMessage());
            }
        }
        outboxRepository.saveAll(outboxes);
    }

    @Transactional
    public void enqueue(String aggregateType, Long aggregateId, String eventType, Map<String, Object> payload) {
        JsonNode json;
        try {
            json = objectMapper.valueToTree(payload);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to serialize outbox payload for aggregateType={}, aggregateId={}: {}",
                    aggregateType, aggregateId, e.getMessage());
            return;
        }
        CatalogSyncOutbox outbox = CatalogSyncOutbox.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payloadJson(json)
                .status("PENDING")
                .retryCount(0)
                .build();
        outboxRepository.save(outbox);
    }
}
