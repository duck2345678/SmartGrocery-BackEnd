package com.smartgrocery.backend.service.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NeedExtractionServiceTest {

    private final NeedExtractionService service = new NeedExtractionService();

    @Test
    void analyze_mixedComplaint_extractsNeedsAndConstraintsWithoutNewIntentPerSentence() {
        NeedExtractionService.NeedAnalysis analysis = service.analyze(
                "Nhà dơ quá trời nóng quá khát nước quá thèm ngọt quá hết nước rửa chén, cuối tháng hết tiền rồi"
        );

        assertThat(analysis.hasNeed(NeedExtractionService.Need.HOUSEHOLD_CLEANING)).isTrue();
        assertThat(analysis.hasNeed(NeedExtractionService.Need.DRINK)).isTrue();
        assertThat(analysis.hasNeed(NeedExtractionService.Need.SNACK_SWEET)).isTrue();
        assertThat(analysis.hasNeed(NeedExtractionService.Need.DISHWASHING)).isTrue();
        assertThat(analysis.hasConstraint(NeedExtractionService.Constraint.LOW_BUDGET)).isTrue();
        assertThat(analysis.hasConstraint(NeedExtractionService.Constraint.HOT_WEATHER)).isTrue();
        assertThat(analysis.hasConstraint(NeedExtractionService.Constraint.THIRSTY)).isTrue();
        assertThat(analysis.directProductTerms()).contains("nuoc rua chen");
    }

    @Test
    void recipeAndProductTerms_areExtractedAsReusableSignals() {
        NeedExtractionService.NeedAnalysis recipe = service.analyze("Tạo danh sách mua sắm cho salad healthy");
        NeedExtractionService.NeedAnalysis products = service.analyze("Tạo danh sách mua sắm cho hạt nêm và dầu ăn");

        assertThat(recipe.recipeKey()).contains("SALAD_HEALTHY");
        assertThat(recipe.hasNeed(NeedExtractionService.Need.DIRECT_RECIPE)).isTrue();
        assertThat(products.directProductTerms()).containsExactly("hat nem", "dau an");
        assertThat(products.hasNeed(NeedExtractionService.Need.DIRECT_PRODUCT)).isTrue();
        assertThat(service.recipeKey("Tạo danh sách mua sắm cho mì Ý")).contains("MI_Y");
        assertThat(service.isMealOrDietIntent("mì Ý")).isTrue();
    }

    @Test
    void coffeeParaphrases_shareGoalSignature() {
        assertThat(service.mealGoalSignature("gợi ý món ăn đi kèm cà phê")).isEqualTo("MEAL_WITH_COFFEE");
        assertThat(service.mealGoalSignature("ăn gì hợp với cà phê")).isEqualTo("MEAL_WITH_COFFEE");
        assertThat(service.mealGoalSignature("có món nào uống với cà phê không")).isEqualTo("MEAL_WITH_COFFEE");
    }
}
