package com.smartgrocery.backend.repository.graph;

import com.smartgrocery.backend.entity.graph.KeywordNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface KeywordNodeRepository extends Neo4jRepository<KeywordNode, String> {
    
    @Query("MATCH (k:Keyword {name: $name})-[:MAPS_TO_GOAL|MAPS_TO_PREF]->(target) RETURN target.name LIMIT 5")
    java.util.List<String> findMappedGoalsAndPrefs(String name);
}
