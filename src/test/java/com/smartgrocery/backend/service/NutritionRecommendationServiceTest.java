package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NutritionRecommendationServiceTest {

    @Mock
    private ProductNodeRepository productNodeRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private NutritionRecommendationService recommenderService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getSafeRecommendations_Cached_ReturnsFromCache() {
        // Arrange
        Long userId = 1L;
        List<ProductNode> cachedNodes = List.of(new ProductNode());
        when(valueOperations.get(anyString())).thenReturn(cachedNodes);

        // Act
        List<ProductNode> result = recommenderService.getSafeRecommendations(userId, null, 10);

        // Assert
        assertEquals(1, result.size());
        verify(productNodeRepository, never()).findRecommendedProductsForUser(anyLong(), anyInt());
    }

    @Test
    void getSafeRecommendations_NoCache_NoVector_FetchesFromNeo4jAndFilters() {
        // Arrange
        Long userId = 1L;
        when(valueOperations.get(anyString())).thenReturn(null);

        ProductNode safeNode = new ProductNode();
        safeNode.setProductId(100L);
        
        ProductNode conflictNode = new ProductNode();
        conflictNode.setProductId(200L);

        when(productNodeRepository.findRecommendedProductsForUser(eq(userId), anyInt()))
                .thenReturn(List.of(safeNode, conflictNode));
                
        when(productNodeRepository.findConflictingProductsForUser(eq(userId), anyList()))
                .thenReturn(List.of(conflictNode));

        // Act
        List<ProductNode> result = recommenderService.getSafeRecommendations(userId, null, 10);

        // Assert
        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getProductId());
        
        // Verify cache setting
        verify(valueOperations).set(anyString(), anyList(), eq(2L), eq(TimeUnit.HOURS));
    }

    @Test
    void getSafeRecommendations_WithVector_FetchesFromNeo4jVectorSearch() {
        // Arrange
        Long userId = 1L;
        List<Double> vector = List.of(0.1, 0.2, 0.3);
        when(valueOperations.get(anyString())).thenReturn(null);

        ProductNode safeNode = new ProductNode();
        safeNode.setProductId(300L);

        when(productNodeRepository.searchByVector(eq(vector), anyDouble(), anyInt()))
                .thenReturn(List.of(safeNode));
                
        when(productNodeRepository.findConflictingProductsForUser(eq(userId), anyList()))
                .thenReturn(List.of());

        // Act
        List<ProductNode> result = recommenderService.getSafeRecommendations(userId, vector, 10);

        // Assert
        assertEquals(1, result.size());
        verify(productNodeRepository).searchByVector(eq(vector), anyDouble(), anyInt());
        verify(productNodeRepository, never()).findRecommendedProductsForUser(anyLong(), anyInt());
    }

    @Test
    void fallbackRecommendations_ReturnsBasicQuery() {
        // Arrange
        Long userId = 1L;
        ProductNode node = new ProductNode();
        node.setProductId(400L);
        
        when(productNodeRepository.findRecommendedProductsForUser(userId, 5))
                .thenReturn(List.of(node));

        // Act
        List<ProductNode> result = recommenderService.fallbackRecommendations(userId, null, 5, new RuntimeException("DB Timeout"));

        // Assert
        assertEquals(1, result.size());
        assertEquals(400L, result.get(0).getProductId());
    }
}
