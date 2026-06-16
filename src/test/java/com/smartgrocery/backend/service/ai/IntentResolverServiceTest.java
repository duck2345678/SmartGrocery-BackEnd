package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.ShoppingScenario;
import com.smartgrocery.backend.entity.ShoppingScenarioAlias;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.ChatSessionRepository;
import com.smartgrocery.backend.entity.ChatSession;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentResolverServiceTest {

    private CatalogCacheService catalogCacheService;
    private ProductVariantRepository productVariantRepository;
    private OpenRouterClient openRouterClient;
    private ObjectMapper objectMapper;
    private ConversationStateManager stateManager;
    private ChatSessionRepository chatSessionRepository;
    private IntentResolverService resolverService;

    @BeforeEach
    void setUp() {
        catalogCacheService = mock(CatalogCacheService.class);
        productVariantRepository = mock(ProductVariantRepository.class);
        openRouterClient = mock(OpenRouterClient.class);
        objectMapper = new ObjectMapper();
        stateManager = mock(ConversationStateManager.class);
        chatSessionRepository = mock(ChatSessionRepository.class);

        resolverService = new IntentResolverService(
                catalogCacheService,
                productVariantRepository,
                openRouterClient,
                objectMapper,
                stateManager,
                chatSessionRepository
        );
    }

    @Test
    void testSelectionRules() {
        IntentResolverService.IntentResult result1 = resolverService.resolveIntent("1", "sess-1", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.MEAL_SELECTION, result1.intent());

        IntentResolverService.IntentResult result2 = resolverService.resolveIntent("món 2", "sess-1", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.MEAL_SELECTION, result2.intent());
    }

    @Test
    void testDiscountRules() {
        IntentResolverService.IntentResult result = resolverService.resolveIntent("Tr\u1ee9ng c\u00f3 gi\u1ea3m gi\u00e1 kh\u00f4ng", "sess-1", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.CHECK_DISCOUNT, result.intent());
        assertEquals("Tr\u1ee9ng", result.entity());
    }

    @Test
    void testExactProductMatch() {
        Product p = Product.builder().id(1L).name("Sữa tươi Vinamilk").build();
        ProductVariant v = ProductVariant.builder().id(10L).product(p).build();
        
        when(productVariantRepository.findActiveVariantsByKeywordNameOnly("sua tuoi vinamilk"))
                .thenReturn(List.of(v));

        IntentResolverService.IntentResult result = resolverService.resolveIntent("mua sua tuoi vinamilk", "sess-1", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.BUY_PRODUCT, result.intent());
        assertEquals("sua tuoi vinamilk", result.entity());
    }

    @Test
    void testFuzzyProductMatch() {
        Product p = Product.builder().id(1L).name("S\u1eefa t\u01b0\u01a1i Vinamilk").build();
        ProductVariant v = ProductVariant.builder().id(10L).product(p).build();

        when(productVariantRepository.findActiveVariantsByKeywordNameOnly("sua tuoi"))
                .thenReturn(List.of());
        when(productVariantRepository.searchActiveForSubstitution("sua tuoi"))
                .thenReturn(List.of(v));

        IntentResolverService.IntentResult result = resolverService.resolveIntent("mua sua tuoi", "sess-1", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.BUY_PRODUCT, result.intent());
        assertEquals("sua tuoi", result.entity());
        assertEquals(0.9, result.confidence());
    }

    @Test
    void testFuzzyProductMatchFalsePositive() {
        Product p = Product.builder().id(1L).name("S\u1eefa t\u01b0\u01a1i Vinamilk").build();
        ProductVariant v = ProductVariant.builder().id(10L).product(p).build();

        when(productVariantRepository.findActiveVariantsByKeywordNameOnly("uong sua"))
                .thenReturn(List.of());
        when(productVariantRepository.searchActiveForSubstitution("uong sua"))
                .thenReturn(List.of(v));

        IntentResolverService.IntentResult result = resolverService.resolveIntent("mua uong sua", "sess-1", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.GENERAL_CHAT, result.intent());
    }

    @Test
    void testMealMatch() {
        Meal m = Meal.builder().id(1L).name("Canh Chua C\u00e1 H\u1ed3i").build();
        when(catalogCacheService.getCachedMeals()).thenReturn(List.of(m));

        IntentResolverService.IntentResult result = resolverService.resolveIntent("n\u1ea5u canh chua c\u00e1 h\u1ed3i", "sess-1", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.MEAL_RECIPE, result.intent());
        assertEquals("Canh Chua C\u00e1 H\u1ed3i", result.entity());
    }

    @Test
    void testScenarioAliasMatch() {
        ShoppingScenario s = ShoppingScenario.builder().code("CLEANING").name("D\u1ecdn d\u1eb9p").build();
        ShoppingScenarioAlias alias = ShoppingScenarioAlias.builder().id(1L).normalizedAlias("nha do").scenario(s).build();
        
        when(catalogCacheService.getCachedScenarioAliases()).thenReturn(List.of(alias));

        IntentResolverService.IntentResult result = resolverService.resolveIntent("nh\u00e0 d\u01a1 qu\u00e1", "sess-1", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.SHOPPING_SCENARIO, result.intent());
        assertEquals("CLEANING", result.entity());
    }

    @Test
    void testScenarioAliasMatchFalsePositive() {
        ShoppingScenario s = ShoppingScenario.builder().code("CLEANING").name("D\u1ecdn d\u1eb9p").build();
        ShoppingScenarioAlias alias = ShoppingScenarioAlias.builder().id(1L).normalizedAlias("nha do").scenario(s).build();
        
        when(catalogCacheService.getCachedScenarioAliases()).thenReturn(List.of(alias));

        IntentResolverService.IntentResult result = resolverService.resolveIntent("nh\u00e0 d\u01a1m c\u00e2y", "sess-1", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.GENERAL_CHAT, result.intent());
    }

    @Test
    void testContextContinuation() {
        ConversationStateManager.ConversationState state = ConversationStateManager.ConversationState.builder()
                .lastIntent("SHOPPING_SCENARIO")
                .scenarioCode("PICNIC")
                .build();
        when(stateManager.getState("sess-1")).thenReturn(state);

        IntentResolverService.IntentResult result = resolverService.resolveIntent("Cho th\u00eam 5 ng\u01b0\u1eddi n\u1eefa", "sess-1", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.SHOPPING_SCENARIO, result.intent());
        assertEquals("PICNIC", result.entity());
    }

    @Test
    void testContextContinuationFromDatabase() {
        ChatSession session = ChatSession.builder()
                .id(12345L)
                .contextType("SCENARIO:PICNIC")
                .build();
        when(chatSessionRepository.findById(12345L)).thenReturn(Optional.of(session));

        IntentResolverService.IntentResult result = resolverService.resolveIntent("Cho th\u00eam 5 ng\u01b0\u1eddi n\u1eefa", "12345", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.SHOPPING_SCENARIO, result.intent());
        assertEquals("PICNIC", result.entity());
    }

    @Test
    void testOpenRouterFallback() {
        String mockAiResponse = "{\"intent\":\"MEAL_RECIPE\",\"confidence\":0.95,\"entity\":\"c\u01a1m chi\u00ean\",\"reason\":\"User asks what to eat today\"}";
        OpenRouterClient.AiCompletionResult aiCompletionResult = OpenRouterClient.AiCompletionResult.builder()
                .reply(mockAiResponse)
                .success(true)
                .build();
        when(openRouterClient.chatCompletion(any(), any(), any(), any()))
                .thenReturn(Mono.just(aiCompletionResult));

        IntentResolverService.IntentResult result = resolverService.resolveIntent("\u0103n g\u00ec h\u00f4m nay n\u1ec9", "sess-1", 1L);
        assertEquals(IntentResolverService.IntentResult.IntentType.MEAL_RECIPE, result.intent());
        assertEquals("c\u01a1m chi\u00ean", result.entity());
        assertEquals(0.95, result.confidence());
    }
}
