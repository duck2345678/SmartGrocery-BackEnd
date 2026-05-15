package com.smartgrocery.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SynonymSeeder {

    private final Neo4jClient neo4jClient;

    @Value("${app.neo4j.legacy-synonym-seed.enabled:false}")
    private boolean legacySynonymSeedEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void seedSynonyms() {
        if (!legacySynonymSeedEnabled) {
            log.info("Legacy SynonymSeeder disabled. Synonyms are rebuilt from current product catalog.");
            return;
        }
        try {
            log.info("Seeding Neo4j Synonyms for improved intent matching...");
            
            // Map "lẩu" to some products (if they exist) or create a category link
            // For now, we create Synonym nodes and MAPS_TO relationships
            
            String[] commands = {
                "MERGE (s:Synonym {name: 'lẩu'})",
                "MERGE (s:Synonym {name: 'lau'})",
                "MERGE (s:Synonym {name: 'nuoc lau'})",
                "MERGE (s:Synonym {name: 'nước lau'})",
                
                // Link "lẩu" to products containing "lẩu" or categories
                "MATCH (s:Synonym {name: 'lẩu'}), (p:Product) WHERE p.name CONTAINS 'Lẩu' MERGE (s)-[:MAPS_TO]->(p)",
                
                // Link "lau" specifically to cleaning products
                "MATCH (s:Synonym {name: 'lau'}), (p:Product) WHERE p.name CONTAINS 'Lau' AND NOT p.name CONTAINS 'Lẩu' MERGE (s)-[:MAPS_TO]->(p)",
                "MATCH (s:Synonym {name: 'nuoc lau'}), (p:Product) WHERE p.name CONTAINS 'Lau' MERGE (s)-[:MAPS_TO]->(p)"
            };

            for (String cmd : commands) {
                neo4jClient.query(cmd).run();
            }

            log.info("Synonym seeding completed.");
        } catch (Exception e) {
            log.warn("Synonym seeding failed: {}", e.getMessage());
        }
    }
}
