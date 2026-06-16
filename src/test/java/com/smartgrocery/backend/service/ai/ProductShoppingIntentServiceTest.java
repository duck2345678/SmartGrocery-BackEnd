package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductShoppingIntentServiceTest {

    @Test
    void explicitBuyProductBuildsProductShoppingItems() {
        ProductVariantRepository repository = mock(ProductVariantRepository.class);
        ProductShoppingIntentService service = service(repository);
        when(repository.findTop10ActiveByKeyword("trung"))
                .thenReturn(List.of(variant(1L, 101L, "Trung ga")));

        ProductShoppingIntentService.ProductShoppingResult result =
                service.detectProductShoppingIntent("toi muon mua trung");

        assertNotNull(result.shoppingItems());
        assertEquals(1, result.shoppingItems().size());
        assertEquals("Trung ga", result.shoppingItems().get(0).getName());
    }

    @Test
    void mealIngredientRequestDoesNotBecomeProductIntent() {
        ProductVariantRepository repository = mock(ProductVariantRepository.class);
        ProductShoppingIntentService service = service(repository);

        ProductShoppingIntentService.ProductShoppingResult result =
                service.detectProductShoppingIntent("mua nguyen lieu nau pho");

        assertNull(result.reply());
        assertNull(result.shoppingItems());
    }

    @Test
    void buyKeywordExtractorKeepsOnlyProductPhrase() {
        ProductShoppingIntentService service = service(mock(ProductVariantRepository.class));

        assertEquals("trung", service.extractProductKeyword("toi muon mua trung"));
    }

    private ProductShoppingIntentService service(ProductVariantRepository repository) {
        return new ProductShoppingIntentService(repository, new ShoppingItemBuilder(repository));
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
