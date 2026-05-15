package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.config.OpenRouterConfig;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.UserNutritionProfile;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.UserNutritionProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import com.smartgrocery.backend.dto.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SemanticAllergyGuardServiceTest {

    private SemanticAllergyGuardService service;

    @Mock private OpenRouterClient openRouterClient;
    @Mock private ProductRepository productRepository;
    @Mock private UserNutritionProfileRepository nutritionProfileRepository;
    @Mock private OpenRouterConfig openRouterConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SemanticAllergyGuardService(
                openRouterClient,
                productRepository,
                nutritionProfileRepository,
                objectMapper,
                openRouterConfig
        );
        when(openRouterConfig.getPass1Model()).thenReturn("fast-model");
    }

    @Test
    void testEnforceSemanticGuard_RemovesDirectAllergen() throws Exception {
        // Arrange
        Long userId = 1L;
        String allergies = "Cà chua";
        UserNutritionProfile profile = new UserNutritionProfile();
        profile.setAllergies(allergies);
        when(nutritionProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        Product ketchup = Product.builder().id(101L).name("Ketchup").description("Sốt cà chua đậm đặc").build();
        Product milk = Product.builder().id(102L).name("Sữa tươi").description("Sữa bò nguyên chất").build();
        when(productRepository.findAllByIdWithCategory(any())).thenReturn(List.of(ketchup, milk));

        ChatResponsePayload payload = new ChatResponsePayload();
        payload.setReply("Đây là các món ăn.");
        payload.getProposedItems().add(ProposedItemDto.builder().productId(101L).build());
        payload.getProposedItems().add(ProposedItemDto.builder().productId(102L).build());

        // Mock LLM Response
        String llmReply = "[{\"id\": 101, \"dangerLevel\": \"DIRECT_ALLERGEN\", \"reason\": \"Chứa cà chua\"}, {\"id\": 102, \"dangerLevel\": \"SAFE\", \"reason\": \"\"}]";
        OpenRouterClient.AiCompletionResult aiResult = OpenRouterClient.AiCompletionResult.builder()
                .success(true)
                .reply(llmReply)
                .build();
        when(openRouterClient.chatCompletion(anyString(), anyList(), anyList(), anyString(), any())).thenReturn(Mono.just(aiResult));

        // Act
        service.enforceSemanticGuard(payload, userId);

        // Assert
        assertEquals(1, payload.getProposedItems().size());
        assertEquals(102L, payload.getProposedItems().get(0).getProductId());
        assertTrue(payload.getRemoveReasons().containsKey(101L));
        assertTrue(payload.getReply().contains("Đã loại bỏ một số sản phẩm không an toàn"));
    }

    @Test
    void testEnforceSemanticGuard_MayContain_AddsExplanation() throws Exception {
        // Arrange
        Long userId = 1L;
        String allergies = "Hải sản";
        UserNutritionProfile profile = new UserNutritionProfile();
        profile.setAllergies(allergies);
        when(nutritionProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        Product nom = Product.builder().id(201L).name("Nộm ngũ sắc").description("Gồm rau củ và nước sốt").build();
        when(productRepository.findAllByIdWithCategory(any())).thenReturn(List.of(nom));

        ChatResponsePayload payload = new ChatResponsePayload();
        payload.getProposedItems().add(ProposedItemDto.builder().productId(201L).build());

        // Mock LLM Response
        String llmReply = "[{\"id\": 201, \"dangerLevel\": \"MAY_CONTAIN\", \"reason\": \"Thường có nước mắm cá\"}]";
        OpenRouterClient.AiCompletionResult aiResult = OpenRouterClient.AiCompletionResult.builder()
                .success(true)
                .reply(llmReply)
                .build();
        when(openRouterClient.chatCompletion(anyString(), anyList(), anyList(), anyString(), any())).thenReturn(Mono.just(aiResult));

        // Act
        service.enforceSemanticGuard(payload, userId);

        // Assert
        assertEquals(1, payload.getProposedItems().size());
        assertTrue(payload.getExplanations().containsKey(201L));
        assertTrue(payload.getExplanations().get(201L).contains("Thường có nước mắm cá"));
    }

    @Test
    void testEnforceSemanticGuard_Caching() throws Exception {
        // Arrange
        Long userId = 1L;
        String allergies = "Lạc";
        UserNutritionProfile profile = new UserNutritionProfile();
        profile.setAllergies(allergies);
        when(nutritionProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        Product snack = Product.builder().id(301L).name("Snack").build();
        when(productRepository.findAllByIdWithCategory(any())).thenReturn(List.of(snack));

        ChatResponsePayload payload = new ChatResponsePayload();
        payload.getProposedItems().add(ProposedItemDto.builder().productId(301L).build());

        String llmReply = "[{\"id\": 301, \"dangerLevel\": \"SAFE\", \"reason\": \"\"}]";
        OpenRouterClient.AiCompletionResult aiResult = OpenRouterClient.AiCompletionResult.builder()
                .success(true)
                .reply(llmReply)
                .build();
        when(openRouterClient.chatCompletion(anyString(), anyList(), anyList(), anyString(), any())).thenReturn(Mono.just(aiResult));

        // Act - Call 1 (hits LLM)
        service.enforceSemanticGuard(payload, userId);
        
        // Act - Call 2 (should hit cache)
        ChatResponsePayload payload2 = new ChatResponsePayload();
        payload2.getProposedItems().add(ProposedItemDto.builder().productId(301L).build());
        service.enforceSemanticGuard(payload2, userId);

        // Assert
        verify(openRouterClient, times(1)).chatCompletion(anyString(), anyList(), anyList(), anyString(), any());
    }
}
