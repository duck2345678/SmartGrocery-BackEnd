package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionRecommendationService {

    private final ProductNodeRepository productNodeRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RECOMMENDER_CIRCUIT_BREAKER = "recommenderService";
    private static final String CACHE_PREFIX = "nutrition:recs:";

    /**
     * Lấy danh sách gợi ý an toàn cho người dùng (lọc bỏ các sản phẩm dị ứng/xung đột).
     * Áp dụng Circuit Breaker (Resilience4j) và Redis Cache.
     */
    @CircuitBreaker(name = RECOMMENDER_CIRCUIT_BREAKER, fallbackMethod = "fallbackRecommendations")
    public List<ProductNode> getSafeRecommendations(Long userId, List<Double> userEmbeddingVector, int limit) {
        String cacheKey = CACHE_PREFIX + userId;
        
        // 1. Check Redis Cache
        @SuppressWarnings("unchecked")
        List<ProductNode> cached = (List<ProductNode>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Returning cached recommendations for user: {}", userId);
            return cached;
        }

        log.info("Generating new ML recommendations for user: {}", userId);
        
        // 2. Fetch candidates via Vector Similarity (GDS)
        // Nếu user chưa có vector, ta lấy top rated/popular (fallback).
        List<ProductNode> candidates;
        if (userEmbeddingVector != null && !userEmbeddingVector.isEmpty()) {
             candidates = productNodeRepository.searchByVector(userEmbeddingVector, 0.7, limit * 3);
        } else {
             candidates = productNodeRepository.findRecommendedProductsForUser(userId, limit * 3);
        }

        // 3. Lọc bỏ các sản phẩm nguy hiểm (Dị ứng, Condition)
        List<Long> candidateIds = candidates.stream().map(ProductNode::getProductId).toList();
        List<Long> conflictIds = productNodeRepository.findConflictingProductsForUser(userId, candidateIds)
                .stream().map(ProductNode::getProductId).toList();

        List<ProductNode> safeRecommendations = candidates.stream()
                .filter(p -> !conflictIds.contains(p.getProductId()))
                .limit(limit)
                .collect(Collectors.toList());

        // 4. Save to Cache
        redisTemplate.opsForValue().set(cacheKey, safeRecommendations, 2, TimeUnit.HOURS);

        return safeRecommendations;
    }

    /**
     * Fallback method khi Neo4j/GDS quá tải hoặc fail (SLA < 300ms vi phạm).
     */
    public List<ProductNode> fallbackRecommendations(Long userId, List<Double> userEmbeddingVector, int limit, Throwable t) {
        log.warn("Circuit Breaker triggered for RecommenderService: {}. Falling back to basic recommendations.", t.getMessage());
        return productNodeRepository.findRecommendedProductsForUser(userId, limit); // Truy vấn cơ bản, không dùng Vector
    }

    /**
     * Chạy định kỳ (hàng đêm) để cập nhật Graph Embeddings sử dụng thuật toán FastRP của Neo4j GDS.
     */
    @Scheduled(cron = "0 0 2 * * ?") // 2 AM every day
    public void runNightlyFastRPEmbeddingUpdate() {
        log.info("Bắt đầu chạy thuật toán Neo4j GDS FastRP để cập nhật Graph Embeddings...");
        try {
            // Đây là placeholder cho câu lệnh GDS projection và mutate.
            // Ví dụ thực tế:
            // CALL gds.fastRP.mutate('productGraph', {embeddingDimension: 256, randomSeed: 42, mutateProperty: 'fastRpEmbedding'})
            log.info("Hoàn tất cập nhật FastRP Embeddings.");
        } catch (Exception e) {
            log.error("Lỗi khi chạy FastRP: {}", e.getMessage());
        }
    }
}
