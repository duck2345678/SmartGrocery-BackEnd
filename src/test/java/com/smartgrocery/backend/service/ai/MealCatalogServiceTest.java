package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MealCatalogServiceTest {

    @Test
    void dinnerSuggestions_areLoadedFromCatalogInsteadOfHardCodedTemplates() {
        MealCatalogService service = new MealCatalogService(new ObjectMapper());
        ReflectionTestUtils.setField(
                service,
                "catalogResource",
                new ClassPathResource("data/international_food_dataset_1000_vi.json")
        );

        var options = service.suggestMealOptions("Gợi ý bữa tối", 0, Set.of(), 3);

        assertThat(options).hasSize(3);
        assertThat(options)
                .extracting(MealCatalogService.CatalogMealOption::ingredients)
                .allSatisfy(ingredients -> assertThat(ingredients).hasSizeGreaterThanOrEqualTo(2));
        assertThat(options)
                .extracting(MealCatalogService.CatalogMealOption::title)
                .doesNotContain(
                        "Ức gà áp chảo + măng tây + khoai lang",
                        "Đậu hũ sốt nấm + su hào luộc + yến mạch mặn",
                        "Trứng luộc + salad rau xanh + táo"
                );
    }

    @Test
    void avoidanceTokens_filterCatalogMealsBeforeReturningOptions() {
        MealCatalogService service = new MealCatalogService(new ObjectMapper());
        ReflectionTestUtils.setField(
                service,
                "catalogResource",
                new ClassPathResource("data/international_food_dataset_1000_vi.json")
        );

        var options = service.suggestMealOptions("Gợi ý bữa tối", 0, Set.of("hai san", "ca"), 5);

        assertThat(options).isNotEmpty();
        assertThat(options).allSatisfy(option ->
                assertThat(String.join(" ", option.ingredients()).toLowerCase())
                        .doesNotContain("cá")
        );
    }
}
