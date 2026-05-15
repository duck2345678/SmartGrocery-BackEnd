package com.smartgrocery.backend.repository.graph;

import com.smartgrocery.backend.entity.graph.SemanticCacheNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SemanticCacheRepository extends Neo4jRepository<SemanticCacheNode, String> {

    /**
     * Tìm kiếm câu hỏi tương đồng nhất dựa trên vector embedding.
     * Sử dụng cosine similarity (1 - vector distance).
     */
    @Query("""
            MATCH (c:SemanticCache)
            WHERE c.embedding IS NOT NULL
            WITH c, gds.similarity.cosine(c.embedding, $queryVector) AS similarity
            WHERE similarity > $threshold
            RETURN c
            ORDER BY similarity DESC
            LIMIT 1
            """)
    Optional<SemanticCacheNode> findSimilarAnswer(List<Double> queryVector, Double threshold);

    /**
     * Tìm chính xác theo hash (L2 cache - không cần gọi embedding API).
     */
    Optional<SemanticCacheNode> findByQuestionHash(String questionHash);

    @Query("MATCH (c:SemanticCache {questionHash: $hash}) SET c.useCount = c.useCount + 1, c.lastUsedAt = datetime() RETURN c")
    void incrementUseCount(String hash);

    @Query("MATCH (c:SemanticCache {questionHash: $hash}) SET c.trustScore = c.trustScore + $delta RETURN c")
    void updateTrustScore(String hash, Double delta);
}
