package com.smartgrocery.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.dto.ChatResponseDto;
import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.Wishlist;
import com.smartgrocery.backend.entity.WishlistItem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiChatControllerMealMatchTest {

    @Test
    void phoQueryDoesNotMatchCheeseMeal() throws Exception {
        AiChatController controller = new AiChatController();
        Method matcher = AiChatController.class.getDeclaredMethod("findMealByFuzzyName", List.class, String.class);
        matcher.setAccessible(true);

        Meal pho = Meal.builder().id(1L).name("Pho Bo Truyen Thong Sang").build();
        Meal clamCheese = Meal.builder().id(2L).name("Ngheu Nuong Pho Mai Toi").build();

        Meal matched = (Meal) matcher.invoke(controller, List.of(clamCheese, pho), "toi muon an pho");

        assertEquals(pho.getId(), matched.getId());
    }

    @Test
    void multiSelectionCombinesMeals() throws Exception {
        AiChatController controller = new AiChatController();
        com.smartgrocery.backend.repository.jpa.ProductVariantRepository repository =
                mock(com.smartgrocery.backend.repository.jpa.ProductVariantRepository.class);
        Field repositoryField = AiChatController.class.getDeclaredField("productVariantRepository");
        repositoryField.setAccessible(true);
        repositoryField.set(controller, repository);
        Method detectSelection = selectionMethod();

        Meal pho = Meal.builder().id(1L).name("Pho Bo").build();
        Meal bun = Meal.builder().id(2L).name("Bun Cha").build();

        Map<Long, List<MealIngredient>> ingredientsByMeal = new LinkedHashMap<>();
        ingredientsByMeal.put(1L, List.of(
                ingredient(101L, "Than bo", "PRIMARY"),
                ingredient(102L, "Banh pho kho", "SECONDARY")
        ));
        ingredientsByMeal.put(2L, List.of(
                ingredient(201L, "Thit heo", "PRIMARY"),
                ingredient(202L, "Bun tuoi", "SECONDARY")
        ));

        when(repository.findByProduct_IdInAndStatus(List.of(101L, 102L), "ACTIVE"))
                .thenReturn(List.of(variant(1001L, 101L, "Than bo"), variant(1002L, 102L, "Banh pho kho")));
        when(repository.findByProduct_IdInAndStatus(List.of(201L, 202L), "ACTIVE"))
                .thenReturn(List.of(variant(2001L, 201L, "Thit heo"), variant(2002L, 202L, "Bun tuoi")));

        List<Map<String, String>> messages = List.of(
                Map.of("role", "assistant", "content", "1. Pho Bo\n2. Bun Cha\n3. Com Tam")
        );

        Object result = detectSelection.invoke(
                controller,
                "1 va 2",
                messages,
                List.of(pho, bun),
                ingredientsByMeal
        );

        String reply = (String) result.getClass().getDeclaredMethod("reply").invoke(result);
        @SuppressWarnings("unchecked")
        List<ChatResponseDto.ShoppingItem> shoppingItems =
                (List<ChatResponseDto.ShoppingItem>) result.getClass().getDeclaredMethod("shoppingItems").invoke(result);

        assertTrue(reply.contains("Pho Bo"));
        assertTrue(reply.contains("Bun Cha"));
        assertNotNull(shoppingItems);
        assertEquals(4, shoppingItems.size());
    }

    @Test
    void outOfBoundsSelectionReturnsHelpfulReply() throws Exception {
        AiChatController controller = new AiChatController();
        Method detectSelection = selectionMethod();

        Object result = detectSelection.invoke(
                controller,
                "6",
                List.of(Map.of("role", "assistant", "content", "1. Pho Bo\n2. Bun Cha\n3. Com Tam\n4. Mi Quang\n5. Hu Tieu")),
                List.of(Meal.builder().id(1L).name("Pho Bo").build()),
                Map.of()
        );

        String reply = (String) result.getClass().getDeclaredMethod("reply").invoke(result);
        @SuppressWarnings("unchecked")
        List<ChatResponseDto.ShoppingItem> shoppingItems =
                (List<ChatResponseDto.ShoppingItem>) result.getClass().getDeclaredMethod("shoppingItems").invoke(result);

        assertTrue(reply.contains("1 den 5") || reply.contains("1 đến 5"));
        assertNull(shoppingItems);
    }

    @Test
    void invalidSelectionTextReturnsGuidance() throws Exception {
        AiChatController controller = new AiChatController();
        Method detectSelection = selectionMethod();

        Object result = detectSelection.invoke(
                controller,
                "chon mon abc",
                List.of(Map.of("role", "assistant", "content", "1. Pho Bo\n2. Bun Cha")),
                List.of(),
                Map.of()
        );

        String reply = (String) result.getClass().getDeclaredMethod("reply").invoke(result);

        assertTrue(reply.contains("so thu tu") || reply.contains("số thứ tự"));
    }

    @Test
    void eggDiscountQueryReturnsDiscountedItems() throws Exception {
        AiChatController controller = discountReadyController();
        Method detectDiscount = discountMethod();

        ProductVariant eggDiscount = discountedVariant(3001L, 301L, "Trung ga");
        when(productVariantRepository(controller).findDiscountedVariantsByKeyword("trung")).thenReturn(List.of(eggDiscount));

        Object result = detectDiscount.invoke(controller, "trung co giam gia khong", 11L, List.of());

        String reply = (String) result.getClass().getDeclaredMethod("reply").invoke(result);
        @SuppressWarnings("unchecked")
        List<ChatResponseDto.ShoppingItem> shoppingItems =
                (List<ChatResponseDto.ShoppingItem>) result.getClass().getDeclaredMethod("shoppingItems").invoke(result);

        assertTrue(reply.toLowerCase().contains("trung ga"));
        assertNotNull(shoppingItems);
        assertEquals(1, shoppingItems.size());
    }

    @Test
    void discountKeywordExtractionDoesNotLeavePartialVietnameseWords() throws Exception {
        AiChatController controller = new AiChatController();
        Method extractKeyword = AiChatController.class.getDeclaredMethod("extractDiscountKeyword", String.class, boolean.class);
        extractKeyword.setAccessible(true);

        String keyword = (String) extractKeyword.invoke(controller, "trung co duoc giam gia khong", false);

        assertEquals("trung", keyword);
    }

    @Test
    void discountKeywordExtractionHandlesVietnameseAccentsAsWholeTokens() throws Exception {
        AiChatController controller = new AiChatController();
        Method extractKeyword = AiChatController.class.getDeclaredMethod("extractDiscountKeyword", String.class, boolean.class);
        extractKeyword.setAccessible(true);

        String keyword = (String) extractKeyword.invoke(
                controller,
                "tr\u1ee9ng c\u00f3 \u0111\u01b0\u1ee3c gi\u1ea3m gi\u00e1 kh\u00f4ng",
                false
        );

        assertEquals("trung", keyword);
    }

    @Test
    void aiDiscountIntentExtractionKeepsProductNameFromJson() throws Exception {
        AiChatController controller = new AiChatController();
        inject(controller, "objectMapper", new ObjectMapper());
        Method parseExtraction = AiChatController.class.getDeclaredMethod("parseDiscountIntentExtraction", String.class);
        parseExtraction.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.Optional<Object> result = (java.util.Optional<Object>) parseExtraction.invoke(
                controller,
                "{\"intent\":\"check_discount\",\"product_name\":\"tr\\u1ee9ng g\\u00e0\"}"
        );

        assertTrue(result.isPresent());
        Method productName = result.get().getClass().getDeclaredMethod("productName");
        productName.setAccessible(true);
        assertEquals("tr\u1ee9ng g\u00e0", productName.invoke(result.get()));
    }

    @Test
    void discountKeywordMustMatchWholeNormalizedPhrase() throws Exception {
        AiChatController controller = discountReadyController();
        Method detectDiscount = discountMethod();

        ProductVariant unrelatedDiscount = discountedVariant(3002L, 302L, "San pham trung bay");
        when(productVariantRepository(controller).findDiscountedVariantsByKeyword("trứng"))
                .thenReturn(List.of(unrelatedDiscount));
        when(productVariantRepository(controller).findDiscountedVariantsByKeyword("trung"))
                .thenReturn(List.of(unrelatedDiscount));
        when(productVariantRepository(controller).findTop10ActiveByKeyword("trứng")).thenReturn(List.of());
        when(productVariantRepository(controller).findActiveVariantsByKeywordNameOnly("trung")).thenReturn(List.of());
        when(productVariantRepository(controller).searchActiveForSubstitution("trung")).thenReturn(List.of());

        Object result = detectDiscount.invoke(controller, "trứng có giảm giá không", 11L, List.of());

        String reply = (String) result.getClass().getDeclaredMethod("reply").invoke(result);
        @SuppressWarnings("unchecked")
        List<ChatResponseDto.ShoppingItem> shoppingItems =
                (List<ChatResponseDto.ShoppingItem>) result.getClass().getDeclaredMethod("shoppingItems").invoke(result);

        assertTrue(reply.toLowerCase().contains("trứng") || reply.toLowerCase().contains("trung"));
        assertNull(shoppingItems);
    }

    @Test
    void milkDiscountQueryReturnsNoDiscountWhenProductExistsButNotOnSale() throws Exception {
        AiChatController controller = discountReadyController();
        Method detectDiscount = discountMethod();

        ProductVariant milkVariant = activeVariant(4001L, 401L, "Sua tuoi");
        when(productVariantRepository(controller).findAllDiscountedVariants()).thenReturn(List.of());
        when(productVariantRepository(controller).findTop10ActiveByKeyword("sua tuoi")).thenReturn(List.of(milkVariant));

        Object result = detectDiscount.invoke(controller, "sua tuoi co giam gia khong", 11L, List.of());

        String reply = (String) result.getClass().getDeclaredMethod("reply").invoke(result);
        @SuppressWarnings("unchecked")
        List<ChatResponseDto.ShoppingItem> shoppingItems =
                (List<ChatResponseDto.ShoppingItem>) result.getClass().getDeclaredMethod("shoppingItems").invoke(result);

        assertTrue(reply.toLowerCase().contains("sua tuoi"));
        assertNull(shoppingItems);
    }

    @Test
    void specificDiscountQuestionWithFillerWordsDoesNotReturnGeneralList() throws Exception {
        AiChatController controller = discountReadyController();
        Method detectDiscount = discountMethod();

        when(productVariantRepository(controller).findDiscountedVariantsByKeyword("sua tuoi")).thenReturn(List.of());
        when(productVariantRepository(controller).findTop10ActiveByKeyword("sua tuoi")).thenReturn(List.of());
        when(productVariantRepository(controller).findActiveVariantsByKeywordNameOnly("sua tuoi")).thenReturn(List.of());
        when(productVariantRepository(controller).searchActiveForSubstitution("sua tuoi")).thenReturn(List.of());
        when(productVariantRepository(controller).findAllDiscountedVariants()).thenReturn(List.of(
                discountedVariant(9101L, 911L, "Banh mi"),
                discountedVariant(9102L, 912L, "Ca phe")
        ));

        Object result = detectDiscount.invoke(controller, "sua tuoi co duoc giam gia khong", 77L, List.of());

        String reply = (String) result.getClass().getDeclaredMethod("reply").invoke(result);
        @SuppressWarnings("unchecked")
        List<ChatResponseDto.ShoppingItem> shoppingItems =
                (List<ChatResponseDto.ShoppingItem>) result.getClass().getDeclaredMethod("shoppingItems").invoke(result);

        assertTrue(reply.toLowerCase().contains("sua tuoi"));
        assertNull(shoppingItems);
    }

    @Test
    void wishlistSaleQueryReturnsDiscountedWishlistProducts() throws Exception {
        AiChatController controller = discountReadyController();
        Method detectDiscount = discountMethod();

        User user = User.builder().id(77L).email("wishlist@test.com").status("ACTIVE").build();
        Wishlist wishlist = Wishlist.builder().id(10L).user(user).build();
        WishlistItem first = WishlistItem.builder()
                .wishlist(wishlist)
                .product(Product.builder().id(501L).name("Tom su").status("ACTIVE").build())
                .build();
        WishlistItem second = WishlistItem.builder()
                .wishlist(wishlist)
                .product(Product.builder().id(502L).name("Sua tuoi").status("ACTIVE").build())
                .build();

        when(wishlistItemRepository(controller).findByWishlist_UserId(77L)).thenReturn(List.of(first, second));
        when(productVariantRepository(controller).findDiscountedVariantsByProductIds(List.of(501L, 502L)))
                .thenReturn(List.of(
                        discountedVariant(5001L, 501L, "Tom su"),
                        discountedVariant(5002L, 502L, "Sua tuoi")
                ));

        Object result = detectDiscount.invoke(controller, "wishlist cua toi co gi dang giam gia", 77L, List.of());

        String reply = (String) result.getClass().getDeclaredMethod("reply").invoke(result);
        @SuppressWarnings("unchecked")
        List<ChatResponseDto.ShoppingItem> shoppingItems =
                (List<ChatResponseDto.ShoppingItem>) result.getClass().getDeclaredMethod("shoppingItems").invoke(result);

        assertTrue(reply.toLowerCase().contains("wishlist"));
        assertNotNull(shoppingItems);
        assertEquals(2, shoppingItems.size());
    }

    @Test
    void specificDiscountQuestionDoesNotFallbackToGeneralDiscountList() throws Exception {
        AiChatController controller = discountReadyController();
        Method detectDiscount = discountMethod();

        when(productVariantRepository(controller).findDiscountedVariantsByKeyword("ca hoi")).thenReturn(List.of());
        when(productVariantRepository(controller).findTop10ActiveByKeyword("cá hồi")).thenReturn(List.of());
        when(productVariantRepository(controller).searchActiveForSubstitution("ca hoi")).thenReturn(List.of());
        when(productVariantRepository(controller).findAllDiscountedVariants()).thenReturn(List.of(
                discountedVariant(9001L, 901L, "Sua tuoi"),
                discountedVariant(9002L, 902L, "Tom su")
        ));

        Object result = detectDiscount.invoke(controller, "cá hồi có giảm giá không", 77L, List.of());

        String reply = (String) result.getClass().getDeclaredMethod("reply").invoke(result);
        @SuppressWarnings("unchecked")
        List<ChatResponseDto.ShoppingItem> shoppingItems =
                (List<ChatResponseDto.ShoppingItem>) result.getClass().getDeclaredMethod("shoppingItems").invoke(result);

        assertTrue(reply.toLowerCase().contains("cá hồi") || reply.toLowerCase().contains("ca hoi"));
        assertNull(shoppingItems);
    }

    private Method selectionMethod() throws NoSuchMethodException {
        Method method = AiChatController.class.getDeclaredMethod(
                "detectAndBuildShoppingSelection",
                String.class,
                List.class,
                List.class,
                Map.class
        );
        method.setAccessible(true);
        return method;
    }

    private Method discountMethod() throws NoSuchMethodException {
        Method method = AiChatController.class.getDeclaredMethod(
                "detectDiscountIntent",
                String.class,
                Long.class,
                List.class
        );
        method.setAccessible(true);
        return method;
    }

    private AiChatController discountReadyController() throws Exception {
        AiChatController controller = new AiChatController();
        inject(controller, "productVariantRepository", mock(com.smartgrocery.backend.repository.jpa.ProductVariantRepository.class));
        inject(controller, "wishlistItemRepository", mock(com.smartgrocery.backend.repository.jpa.WishlistItemRepository.class));
        inject(controller, "objectMapper", new ObjectMapper());
        return controller;
    }

    private com.smartgrocery.backend.repository.jpa.ProductVariantRepository productVariantRepository(AiChatController controller) throws Exception {
        Field field = AiChatController.class.getDeclaredField("productVariantRepository");
        field.setAccessible(true);
        return (com.smartgrocery.backend.repository.jpa.ProductVariantRepository) field.get(controller);
    }

    private com.smartgrocery.backend.repository.jpa.WishlistItemRepository wishlistItemRepository(AiChatController controller) throws Exception {
        Field field = AiChatController.class.getDeclaredField("wishlistItemRepository");
        field.setAccessible(true);
        return (com.smartgrocery.backend.repository.jpa.WishlistItemRepository) field.get(controller);
    }

    private void inject(AiChatController controller, String fieldName, Object value) throws Exception {
        Field field = AiChatController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private MealIngredient ingredient(Long productId, String name, String role) {
        Product product = Product.builder()
                .id(productId)
                .name(name)
                .status("ACTIVE")
                .build();

        return MealIngredient.builder()
                .product(product)
                .role(role)
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
                .netPrice(BigDecimal.valueOf(10000))
                .status("ACTIVE")
                .unit("g")
                .build();
    }

    private ProductVariant discountedVariant(Long variantId, Long productId, String productName) {
        Product product = Product.builder()
                .id(productId)
                .name(productName)
                .status("ACTIVE")
                .build();

        return ProductVariant.builder()
                .id(variantId)
                .product(product)
                .netPrice(BigDecimal.valueOf(10000))
                .compareAtPrice(BigDecimal.valueOf(15000))
                .status("ACTIVE")
                .unit("hop")
                .build();
    }

    private ProductVariant activeVariant(Long variantId, Long productId, String productName) {
        Product product = Product.builder()
                .id(productId)
                .name(productName)
                .status("ACTIVE")
                .build();

        return ProductVariant.builder()
                .id(variantId)
                .product(product)
                .netPrice(BigDecimal.valueOf(10000))
                .status("ACTIVE")
                .unit("hop")
                .build();
    }
}
