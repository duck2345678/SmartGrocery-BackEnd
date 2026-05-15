package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.config.OpenRouterConfig;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.UserNutritionProfile;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import com.smartgrocery.backend.repository.jpa.*;
import com.smartgrocery.backend.service.CartInspectionService;
import com.smartgrocery.backend.service.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatAssistantServiceIntentGuardTest {

    private ChatAssistantService service;
    private NeedExtractionService needExtractionService;
    private ProductCandidateService productCandidateService;
    private ShoppingActionValidator shoppingActionValidator;

    @BeforeEach
    void setUp() {
        ProductRepository productRepository = mock(ProductRepository.class);
        shoppingActionValidator = mock(ShoppingActionValidator.class);
        when(shoppingActionValidator.findActiveStockedProductIds(List.of(100L, 200L)))
                .thenReturn(Set.of(100L, 200L));
        needExtractionService = new NeedExtractionService();
        productCandidateService = new ProductCandidateService(
                productRepository,
                needExtractionService,
                shoppingActionValidator
        );
        UserProfileConstraintService userProfileConstraintService = new UserProfileConstraintService(
                mock(UserNutritionProfileRepository.class)
        );
        service = new ChatAssistantService(
                mock(OpenRouterClient.class),
                mock(ChatSessionRepository.class),
                mock(ChatMessageRepository.class),
                mock(UserRepository.class),
                mock(UserNutritionProfileRepository.class),
                mock(ProductNodeRepository.class),
                mock(CartInspectionService.class),
                mock(NutritionChatIntegrator.class),
                mock(MemmFeedbackService.class),
                mock(PromotionService.class),
                productRepository,
                mock(ProductVariantRepository.class),
                mock(InventoryStockRepository.class),
                new ObjectMapper(),
                mock(TransactionTemplate.class),
                mock(ApplicationEventPublisher.class),
                mock(OpenRouterConfig.class),
                needExtractionService,
                productCandidateService,
                shoppingActionValidator,
                mock(AiAgentTools.class),
                mock(IngredientComparisonService.class),
                userProfileConstraintService,
                mock(SemanticAllergyGuardService.class),
                mock(MealCatalogService.class)
        );
    }


    @Test
    void directProductTerms_areExtractedBeforeGenericPhraseParsing() {
        @SuppressWarnings("unchecked")
        List<String> phrases = (List<String>) ReflectionTestUtils.invokeMethod(
                service,
                "extractDirectProductPhrases",
                "Tạo danh sách mua sắm cho dầu ăn và hạt nêm"
        );

        assertThat(phrases).containsExactly("dau an", "hat nem");
    }

    @Test
    void directProductTerms_keepUserOrderAndDoNotMatchRecipeDescriptions() {
        @SuppressWarnings("unchecked")
        List<String> phrases = (List<String>) ReflectionTestUtils.invokeMethod(
                service,
                "extractDirectProductPhrases",
                "Tạo danh sách mua sắm cho hạt nêm và dầu ăn"
        );

        Product pork = Product.builder()
                .name("Nạc dăm heo")
                .description("Có thể dùng với dầu ăn khi chế biến.")
                .build();

        Boolean matchesOil = ReflectionTestUtils.invokeMethod(
                service,
                "directProductMatchesPhrase",
                pork,
                "dau an"
        );

        assertThat(phrases).containsExactly("hat nem", "dau an");
        assertThat(matchesOil).isFalse();
    }

    @Test
    void knownRecipeTemplates_forceDirectMealRouting() {
        Boolean salad = ReflectionTestUtils.invokeMethod(
                service,
                "isDirectMealShoppingListRequest",
                "Tạo danh sách mua sắm cho salad healthy"
        );
        Boolean chicken = ReflectionTestUtils.invokeMethod(
                service,
                "isDirectMealShoppingListRequest",
                "Tạo danh sách nguyên liệu cho gà kho"
        );

        assertThat(salad).isTrue();
        assertThat(chicken).isTrue();
    }

    @Test
    void coffeeMealParaphrases_shareSameGoalSignature() {
        String first = ReflectionTestUtils.invokeMethod(
                service,
                "mealGoalSignature",
                "gợi ý món ăn đi kèm cà phê"
        );
        String second = ReflectionTestUtils.invokeMethod(
                service,
                "mealGoalSignature",
                "ăn gì hợp với cà phê"
        );
        String third = ReflectionTestUtils.invokeMethod(
                service,
                "mealGoalSignature",
                "có món nào uống với cà phê không"
        );

        assertThat(first).isEqualTo("MEAL_WITH_COFFEE");
        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
    }

    @Test
    void mealAlternativeRequest_usesPreviousMealGoal() {
        String context = "{\"lastMealOptions\":[{\"optionNo\":1,\"title\":\"Ức gà\",\"ingredients\":[\"ức gà\"],\"reason\":\"test\"}],"
                + "\"lastMealOptionsGoal\":\"MEAL_WITH_COFFEE\",\"lastMealOptionsVariant\":1}";

        Boolean alternative = ReflectionTestUtils.invokeMethod(
                service,
                "isMealAlternativeRequest",
                "tạo món khác",
                context
        );
        String effectivePrompt = ReflectionTestUtils.invokeMethod(
                service,
                "effectiveMealOptionsPrompt",
                "tạo món khác",
                context
        );
        Integer nextVariant = ReflectionTestUtils.invokeMethod(
                service,
                "nextMealOptionsVariant",
                effectivePrompt,
                context
        );

        assertThat(alternative).isTrue();
        assertThat(effectivePrompt).isEqualTo("gợi ý món ăn đi kèm cà phê");
        assertThat(nextVariant).isEqualTo(2);
    }

    @Test
    void chickenBreastIngredient_mustNotAcceptChickenWingProduct() {
        Product wing = Product.builder()
                .name("Cánh gà CP")
                .description("Cánh gà tươi")
                .build();
        Product breast = Product.builder()
                .name("Ức gà phi lê")
                .description("Thịt ức gà")
                .build();

        Boolean wingCompatible = ReflectionTestUtils.invokeMethod(
                service,
                "isStrictIngredientProductCompatible",
                "ức gà",
                wing
        );
        Boolean breastCompatible = ReflectionTestUtils.invokeMethod(
                service,
                "isStrictIngredientProductCompatible",
                "ức gà",
                breast
        );

        assertThat(wingCompatible).isFalse();
        assertThat(breastCompatible).isTrue();
    }

    @Test
    void newShoppingListRequest_mustNotReusePreviousCandidateIds() {
        String context = "{\"lastShoppingCandidateIds\":[100,200],\"lastShoppingCandidateMealIntent\":true}";

        Boolean newRequest = ReflectionTestUtils.invokeMethod(
                service,
                "shouldUsePreviousShoppingCandidates",
                "Tạo danh sách mua sắm cho dầu ăn và hạt nêm",
                context
        );
        Boolean confirmation = ReflectionTestUtils.invokeMethod(
                service,
                "shouldUsePreviousShoppingCandidates",
                "Chốt danh sách này",
                context
        );

        assertThat(newRequest).isFalse();
        assertThat(confirmation).isTrue();
    }

    @Test
    void staleShoppingCandidateIds_mustNotBeReusableAfterProductsDisappear() {
        String context = "{\"lastShoppingCandidateIds\":[999],\"lastShoppingCandidateMealIntent\":true}";
        when(shoppingActionValidator.findActiveStockedProductIds(List.of(999L)))
                .thenReturn(Set.of());

        Boolean confirmation = ReflectionTestUtils.invokeMethod(
                service,
                "shouldUsePreviousShoppingCandidates",
                "Chốt danh sách này",
                context
        );

        assertThat(confirmation).isFalse();
    }

    @Test
    void tomatoAllergy_mustRejectTomatoProducts() {
        Product tomato = Product.builder()
                .name("Cà chua Đà Lạt")
                .description("Cà chua tươi")
                .build();
        Product mushroom = Product.builder()
                .name("Nấm mỡ")
                .description("Nấm tươi")
                .build();

        @SuppressWarnings("unchecked")
        Set<String> allergyTokens = (Set<String>) ReflectionTestUtils.invokeMethod(
                service,
                "extractAllergyTokens",
                "cà chua"
        );
        Boolean tomatoRejected = ReflectionTestUtils.invokeMethod(
                service,
                "productViolatesAllergy",
                tomato,
                allergyTokens
        );
        Boolean mushroomRejected = ReflectionTestUtils.invokeMethod(
                service,
                "productViolatesAllergy",
                mushroom,
                allergyTokens
        );

        assertThat(tomatoRejected).isTrue();
        assertThat(mushroomRejected).isFalse();
    }

    @Test
    void negatedAllergyStatement_mustNotCreateAvoidanceToken() {
        Product mushroom = Product.builder()
                .name("Nấm tuyết gói 50g")
                .description("Nấm tươi")
                .build();

        @SuppressWarnings("unchecked")
        Set<String> allergyTokens = (Set<String>) ReflectionTestUtils.invokeMethod(
                service,
                "extractAllergyTokens",
                "Tôi không dị ứng nấm"
        );
        Boolean mushroomRejected = ReflectionTestUtils.invokeMethod(
                service,
                "productViolatesAllergy",
                mushroom,
                allergyTokens
        );

        assertThat(allergyTokens).isEmpty();
        assertThat(mushroomRejected).isFalse();
    }

    @Test
    void negatedAllergyStatement_isHandledAsProfileCorrection() {
        Boolean correction = ReflectionTestUtils.invokeMethod(
                service,
                "isAllergyCorrection",
                "Nhưng tôi không dị ứng nấm"
        );

        assertThat(correction).isTrue();
    }

    @Test
    void allergyCorrection_removesOnlyCorrectedTermFromProfileText() {
        String remaining = userProfileConstraintService().removeClearedAllergyTerms(
                "Hải sản, nấm, cà chua",
                "Nhưng tôi không dị ứng nấm"
        );

        assertThat(remaining).isEqualTo("Hải sản, cà chua");
    }

    @Test
    void profileHealthGoalMentioningMushroom_mustNotBecomeAllergyAvoidance() {
        UserNutritionProfileRepository repository = mock(UserNutritionProfileRepository.class);
        UserNutritionProfile profile = new UserNutritionProfile();
        profile.setAllergies("");
        profile.setDietaryPreference("ăn healthy");
        profile.setHealthGoals("gợi ý món đậu hũ sốt nấm ít calo");
        when(repository.findByUser_Id(1L)).thenReturn(Optional.of(profile));

        UserProfileConstraintService constraints = new UserProfileConstraintService(repository);
        Set<String> terms = constraints.loadAvoidanceTerms(1L);
        Product mushroom = Product.builder()
                .name("Nấm tuyết gói 50g")
                .description("Nấm tươi")
                .build();

        assertThat(terms).doesNotContain("nam");
        assertThat(constraints.violatesProduct(mushroom, terms)).isFalse();
    }

    @Test
    void profileExplicitAvoidanceStillRejectsMushroom() {
        UserNutritionProfileRepository repository = mock(UserNutritionProfileRepository.class);
        UserNutritionProfile profile = new UserNutritionProfile();
        profile.setAllergies("");
        profile.setDietaryPreference("không ăn nấm");
        profile.setHealthGoals("healthy");
        when(repository.findByUser_Id(1L)).thenReturn(Optional.of(profile));

        UserProfileConstraintService constraints = new UserProfileConstraintService(repository);
        Set<String> terms = constraints.loadAvoidanceTerms(1L);
        Product mushroom = Product.builder()
                .name("Nấm tuyết gói 50g")
                .description("Nấm tươi")
                .build();

        assertThat(terms).contains("nam");
        assertThat(constraints.violatesProduct(mushroom, terms)).isTrue();
    }

    @Test
    void structuredFoodConstraints_blockAndAllowIngredientsAreMergedIntoAvoidanceTerms() {
        UserNutritionProfileRepository repository = mock(UserNutritionProfileRepository.class);
        UserNutritionProfile profile = new UserNutritionProfile();
        profile.setAllergies("");
        profile.setDietaryPreference("");
        profile.setHealthGoals("món đậu hũ sốt nấm ít calo");

        UserProfileConstraintService constraints = new UserProfileConstraintService(repository);
        profile.setFoodConstraints(constraints.mergeFoodConstraints(
                null,
                List.of("trứng", "nấm"),
                List.of("nấm"),
                List.of("chiên rán"),
                List.of("ít muối")
        ));
        when(repository.findByUser_Id(1L)).thenReturn(Optional.of(profile));

        Set<String> terms = constraints.loadAvoidanceTerms(1L);

        assertThat(terms).contains("trung", "chien", "fried");
        assertThat(terms).doesNotContain("nam");
        assertThat(constraints.violatesProduct(Product.builder().name("Trứng gà ta").build(), terms)).isTrue();
        assertThat(constraints.violatesProduct(Product.builder().name("Nấm tuyết gói 50g").build(), terms)).isFalse();
        assertThat(constraints.violatesProduct(Product.builder().name("Gà chiên giòn").build(), terms)).isTrue();
    }

    @Test
    void arbitraryAllergyFoods_areLoadedDynamicallyWithoutFoodSpecificBranches() {
        UserNutritionProfileRepository repository = mock(UserNutritionProfileRepository.class);
        UserNutritionProfile profile = new UserNutritionProfile();
        profile.setAllergies("măng tây, trứng, cà rốt");
        profile.setDietaryPreference("");
        profile.setHealthGoals("");
        when(repository.findByUser_Id(1L)).thenReturn(Optional.of(profile));

        UserProfileConstraintService constraints = new UserProfileConstraintService(repository);
        Set<String> terms = constraints.loadAvoidanceTerms(1L);

        assertThat(terms).containsExactly("mang tay", "trung", "ca rot");
        assertThat(constraints.violatesProduct(Product.builder().name("Măng tây xanh").build(), terms)).isTrue();
        assertThat(constraints.violatesProduct(Product.builder().name("Trứng gà ta").build(), terms)).isTrue();
        assertThat(constraints.violatesProduct(Product.builder().name("Cà rốt Đà Lạt").build(), terms)).isTrue();
        assertThat(constraints.violatesProduct(Product.builder().name("Đậu hũ non").build(), terms)).isFalse();
    }

    @Test
    void healthGoalsMentioningArbitraryFoods_mustNotBecomeAvoidanceTerms() {
        UserNutritionProfileRepository repository = mock(UserNutritionProfileRepository.class);
        UserNutritionProfile profile = new UserNutritionProfile();
        profile.setAllergies("");
        profile.setDietaryPreference("ăn healthy");
        profile.setHealthGoals("thực đơn có măng tây, trứng và cà rốt");
        when(repository.findByUser_Id(1L)).thenReturn(Optional.of(profile));

        UserProfileConstraintService constraints = new UserProfileConstraintService(repository);
        Set<String> terms = constraints.loadAvoidanceTerms(1L);

        assertThat(terms).doesNotContain("mang tay", "trung", "ca rot");
        assertThat(constraints.violatesProduct(Product.builder().name("Măng tây xanh").build(), terms)).isFalse();
        assertThat(constraints.violatesProduct(Product.builder().name("Trứng gà ta").build(), terms)).isFalse();
        assertThat(constraints.violatesProduct(Product.builder().name("Cà rốt Đà Lạt").build(), terms)).isFalse();
    }

    @Test
    void profileAvoidance_mustRejectSpicyFriedAndBoiledFoods() {
        Product chiliSauce = Product.builder()
                .name("Tương ớt cay")
                .description("Sốt chili spicy")
                .build();
        Product friedChicken = Product.builder()
                .name("Gà chiên giòn")
                .description("Fried chicken")
                .build();
        Product boiledEgg = Product.builder()
                .name("Trứng luộc")
                .description("Boiled egg")
                .build();
        Product oatmeal = Product.builder()
                .name("Yến mạch")
                .description("Oat")
                .build();

        @SuppressWarnings("unchecked")
        Set<String> avoidanceTokens = (Set<String>) ReflectionTestUtils.invokeMethod(
                service,
                "extractAllergyTokens",
                "không ăn cay, không ăn đồ chiên rán, không ăn luộc"
        );

        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "productViolatesAllergy", chiliSauce, avoidanceTokens)).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "productViolatesAllergy", friedChicken, avoidanceTokens)).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "productViolatesAllergy", boiledEgg, avoidanceTokens)).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "productViolatesAllergy", oatmeal, avoidanceTokens)).isFalse();
    }

    private UserProfileConstraintService userProfileConstraintService() {
        return new UserProfileConstraintService(mock(UserNutritionProfileRepository.class));
    }
}
