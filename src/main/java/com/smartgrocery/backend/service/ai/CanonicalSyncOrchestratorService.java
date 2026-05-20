package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.repository.jpa.IngredientAliasRepository;
import com.smartgrocery.backend.repository.jpa.UnitAliasRepository;
import com.smartgrocery.backend.service.Neo4jCatalogRebuildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CanonicalSyncOrchestratorService {

    private final CanonicalDictionarySeedService dictionarySeedService;
    private final MealIngredientBackfillService mealIngredientBackfillService;
    private final ProductIngredientGraphLinkService productIngredientGraphLinkService;
    private final CatalogSyncOutboxWorker catalogSyncOutboxWorker;
    private final CatalogSyncAdminService catalogSyncAdminService;
    private final IngredientAliasRepository ingredientAliasRepository;
    private final UnitAliasRepository unitAliasRepository;
    private final Neo4jCatalogRebuildService neo4jCatalogRebuildService;
    private final Neo4jClient neo4jClient;

    @Transactional
    public Map<String, Object> runFullSyncAndChecksum() {
        try {
            dictionarySeedService.run(null);
            mealIngredientBackfillService.run(null);
            productIngredientGraphLinkService.run(null);
        } catch (Exception e) {
            log.warn("Canonical sync bootstrap run failed: {}", e.getMessage());
        }

        // Drain outbox in-process for one-shot admin sync
        for (int i = 0; i < 10; i++) {
            catalogSyncOutboxWorker.pollAndSync();
            long pending = catalogSyncAdminService.getQueueStats().getOrDefault("pending", 0L)
                    + catalogSyncAdminService.getQueueStats().getOrDefault("failed", 0L);
            if (pending == 0) {
                break;
            }
        }

        Map<String, Object> graphAudit = neo4jCatalogRebuildService.auditCatalogGraph();
        Map<String, Object> checksum = buildChecksum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncedAt", LocalDateTime.now().toString());
        result.put("queueStats", catalogSyncAdminService.getQueueStats());
        result.put("graphAudit", graphAudit);
        result.put("checksum", checksum);
        return result;
    }

    private Map<String, Object> buildChecksum() {
        long sqlIngredientAliasActive = ingredientAliasRepository.count();
        long sqlUnitAliasActive = unitAliasRepository.count();

        long neoIngredientAlias = graphCount("MATCH (n:IngredientAlias) RETURN count(n) AS c");
        long neoUnitAlias = graphCount("MATCH (n:UnitAlias) RETURN count(n) AS c");

        Map<String, Object> checksum = new LinkedHashMap<>();
        checksum.put("sqlIngredientAlias", sqlIngredientAliasActive);
        checksum.put("neoIngredientAlias", neoIngredientAlias);
        checksum.put("ingredientAliasMatch", sqlIngredientAliasActive == neoIngredientAlias);
        checksum.put("sqlUnitAlias", sqlUnitAliasActive);
        checksum.put("neoUnitAlias", neoUnitAlias);
        checksum.put("unitAliasMatch", sqlUnitAliasActive == neoUnitAlias);
        return checksum;
    }

    private long graphCount(String cypher) {
        try {
            return neo4jClient.query(cypher)
                    .fetchAs(Long.class)
                    .mappedBy((typeSystem, record) -> record.get("c").asLong())
                    .one()
                    .orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }
}
