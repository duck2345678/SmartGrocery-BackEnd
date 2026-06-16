package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.ShoppingScenario;
import com.smartgrocery.backend.entity.ShoppingScenarioAlias;
import com.smartgrocery.backend.entity.ShoppingScenarioItem;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.ShoppingScenarioAliasRepository;
import com.smartgrocery.backend.repository.jpa.ShoppingScenarioRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneralShoppingIntentServiceTest {

    @Test
    void scenarioFromDatabaseBuildsShoppingItemsByCategoryAndKeyword() throws Exception {
        ShoppingScenarioRepository scenarioRepository = mock(ShoppingScenarioRepository.class);
        ShoppingScenarioAliasRepository aliasRepository = mock(ShoppingScenarioAliasRepository.class);
        ProductVariantRepository productVariantRepository = mock(ProductVariantRepository.class);
        GeneralShoppingIntentService service = service(scenarioRepository, aliasRepository, productVariantRepository);

        ShoppingScenario scenario = ShoppingScenario.builder()
                .code("PICNIC")
                .name("Di picnic")
                .items(List.of(
                        scenarioItem("CATEGORY", "CAT_DRINK", 10),
                        scenarioItem("KEYWORD", "khan giay", 20)
                ))
                .build();
        when(scenarioRepository.findActiveByCodeWithItems("PICNIC")).thenReturn(Optional.of(scenario));
        when(productVariantRepository.findActiveByCategoryCode("CAT_DRINK", 3))
                .thenReturn(List.of(variant(1L, 101L, "Nuoc ngot")));
        when(productVariantRepository.findTop10ActiveByKeyword("khan giay"))
                .thenReturn(List.of(variant(2L, 102L, "Khan giay")));

        Object result = invokeParse(service, "{\"intent\":\"shopping_scenario\",\"scenario\":\"PICNIC\",\"keywords\":[]}");
        @SuppressWarnings("unchecked")
        Optional<GeneralShoppingIntentService.GeneralShoppingExtraction> extraction =
                (Optional<GeneralShoppingIntentService.GeneralShoppingExtraction>) result;
        assertTrue(extraction.isPresent());

        GeneralShoppingIntentService.GeneralShoppingResult shoppingResult =
                invokeBuildFromExtraction(service, extraction.get());

        assertNotNull(shoppingResult.shoppingItems());
        assertEquals(2, shoppingResult.shoppingItems().size());
    }

    @Test
    void unknownScenarioFallsBackToAiKeywords() throws Exception {
        ShoppingScenarioRepository scenarioRepository = mock(ShoppingScenarioRepository.class);
        ShoppingScenarioAliasRepository aliasRepository = mock(ShoppingScenarioAliasRepository.class);
        ProductVariantRepository productVariantRepository = mock(ProductVariantRepository.class);
        GeneralShoppingIntentService service = service(scenarioRepository, aliasRepository, productVariantRepository);

        when(scenarioRepository.findActiveByCodeWithItems("BIRTHDAY_PARTY")).thenReturn(Optional.empty());
        when(productVariantRepository.findTop10ActiveByKeyword("banh kem"))
                .thenReturn(List.of(variant(3L, 103L, "Banh kem")));

        Object result = invokeParse(service, "{\"intent\":\"shopping_scenario\",\"scenario\":\"BIRTHDAY_PARTY\",\"keywords\":[\"banh kem\"]}");
        @SuppressWarnings("unchecked")
        Optional<GeneralShoppingIntentService.GeneralShoppingExtraction> extraction =
                (Optional<GeneralShoppingIntentService.GeneralShoppingExtraction>) result;
        assertTrue(extraction.isPresent());

        GeneralShoppingIntentService.GeneralShoppingResult shoppingResult =
                invokeBuildFromExtraction(service, extraction.get());

        assertNotNull(shoppingResult.shoppingItems());
        assertEquals(1, shoppingResult.shoppingItems().size());
        assertEquals("Banh kem", shoppingResult.shoppingItems().get(0).getName());
    }

    @Test
    void scenarioAliasMapsImplicitMessageToDatabaseScenario() {
        ShoppingScenarioRepository scenarioRepository = mock(ShoppingScenarioRepository.class);
        ShoppingScenarioAliasRepository aliasRepository = mock(ShoppingScenarioAliasRepository.class);
        ProductVariantRepository productVariantRepository = mock(ProductVariantRepository.class);
        GeneralShoppingIntentService service = service(scenarioRepository, aliasRepository, productVariantRepository);

        ShoppingScenario cleaning = ShoppingScenario.builder()
                .code("CLEANING")
                .name("Don dep nha cua")
                .items(List.of(scenarioItem("KEYWORD", "nuoc lau san", 10)))
                .build();
        ShoppingScenarioAlias alias = ShoppingScenarioAlias.builder()
                .scenario(cleaning)
                .aliasText("nha do")
                .normalizedAlias("nha do")
                .build();

        when(aliasRepository.findActiveAliases()).thenReturn(List.of(alias));
        when(scenarioRepository.findActiveByCodeWithItems("CLEANING")).thenReturn(Optional.of(cleaning));
        when(productVariantRepository.findTop10ActiveByKeyword("nuoc lau san"))
                .thenReturn(List.of(variant(4L, 104L, "Nuoc lau san")));

        GeneralShoppingIntentService.GeneralShoppingResult result =
                service.detectGeneralShoppingIntent("nha do qua");

        assertNotNull(result.shoppingItems());
        assertEquals("Nuoc lau san", result.shoppingItems().get(0).getName());
    }

    private GeneralShoppingIntentService service(
            ShoppingScenarioRepository scenarioRepository,
            ShoppingScenarioAliasRepository aliasRepository,
            ProductVariantRepository productVariantRepository
    ) {
        ShoppingItemBuilder builder = new ShoppingItemBuilder(productVariantRepository);
        return new GeneralShoppingIntentService(
                scenarioRepository,
                aliasRepository,
                productVariantRepository,
                null,
                new ObjectMapper(),
                builder
        );
    }

    private Object invokeParse(GeneralShoppingIntentService service, String json) throws Exception {
        Method method = GeneralShoppingIntentService.class.getDeclaredMethod("parseShoppingIntentExtraction", String.class);
        method.setAccessible(true);
        return method.invoke(service, json);
    }

    private GeneralShoppingIntentService.GeneralShoppingResult invokeBuildFromExtraction(
            GeneralShoppingIntentService service,
            GeneralShoppingIntentService.GeneralShoppingExtraction extraction
    ) throws Exception {
        Method method = GeneralShoppingIntentService.class.getDeclaredMethod(
                "buildResultFromExtraction",
                GeneralShoppingIntentService.GeneralShoppingExtraction.class
        );
        method.setAccessible(true);
        return (GeneralShoppingIntentService.GeneralShoppingResult) method.invoke(service, extraction);
    }

    private ShoppingScenarioItem scenarioItem(String type, String value, int priority) {
        return ShoppingScenarioItem.builder()
                .entityType(type)
                .entityValue(value)
                .priority(priority)
                .build();
    }

    private ProductVariant variant(Long variantId, Long productId, String productName) {
        Product product = Product.builder()
                .id(productId)
                .name(productName)
                .status("ACTIVE")
                .build();
        return ProductVariant.builder()
                .id(variantId)
                .product(product)
                .status("ACTIVE")
                .netPrice(BigDecimal.valueOf(10000))
                .unit("hop")
                .build();
    }
}
