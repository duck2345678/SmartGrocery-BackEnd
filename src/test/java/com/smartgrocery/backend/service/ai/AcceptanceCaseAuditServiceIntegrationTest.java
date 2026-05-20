package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.Category;
import com.smartgrocery.backend.entity.IngredientAlias;
import com.smartgrocery.backend.entity.IngredientCanonical;
import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import com.smartgrocery.backend.repository.jpa.IngredientAliasRepository;
import com.smartgrocery.backend.repository.jpa.MealIngredientRepository;
import com.smartgrocery.backend.repository.jpa.MealRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@SpringBootTest(classes = {
        IngredientTextNormalizer.class,
        IngredientMatchV2Service.class,
        StockDimensionBridgeService.class,
        AcceptanceCaseAuditService.class
})
class AcceptanceCaseAuditServiceIntegrationTest {

    @Autowired
    private AcceptanceCaseAuditService auditService;

    @MockBean
    private IngredientAliasRepository ingredientAliasRepository;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private ProductNodeRepository productNodeRepository;

    @MockBean
    private MealRepository mealRepository;

    @MockBean
    private MealIngredientRepository mealIngredientRepository;

    private IngredientCanonical otHiemCanonical;
    private IngredientCanonical ngheCanonical;

    @BeforeEach
    void setup() {
        otHiemCanonical = IngredientCanonical.builder()
                .id(1L)
                .canonicalCode("ot_hiem")
                .canonicalNameVi("ớt hiểm")
                .ingredientFamily("spice")
                .defaultDimension("count")
                .active(true)
                .build();
        ngheCanonical = IngredientCanonical.builder()
                .id(2L)
                .canonicalCode("nghe_bot")
                .canonicalNameVi("bột nghệ")
                .ingredientFamily("spice")
                .defaultDimension("mass")
                .active(true)
                .build();

        Product otHiemProduct = product(10L, "Ớt hiểm trái tươi");
        Product cherryProduct = product(11L, "Cherry Mỹ hộp 300g");
        Product ngheProduct = product(12L, "Bột nghệ nguyên chất DH Foods");
        Product caiProduct = product(13L, "Rau cải thìa");
        Product heoXayProduct = product(14L, "Thịt heo xay");

        when(productRepository.findActiveWithCategory()).thenReturn(List.of(
                otHiemProduct, cherryProduct, ngheProduct, caiProduct, heoXayProduct
        ));
        when(productRepository.findAllByIdWithCategory(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return productRepository.findActiveWithCategory().stream().filter(p -> ids.contains(p.getId())).toList();
        });

        when(productNodeRepository.findCanonicalByAliasNorm(anyString(), eq("vi")))
                .thenReturn(Optional.empty());
        when(productNodeRepository.findProductsByCanonicalId(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of());

        lenient().when(ingredientAliasRepository.findFirstByAliasTextNormAndLangAndActiveTrue("ot hiem trai", "vi"))
                .thenReturn(Optional.of(alias(100L, "ot hiem trai", otHiemCanonical)));
        lenient().when(ingredientAliasRepository.findFirstByAliasTextNormAndLangAndActiveTrue("bot nghe", "vi"))
                .thenReturn(Optional.of(alias(101L, "bot nghe", ngheCanonical)));
        lenient().when(ingredientAliasRepository.findFirstByAliasTextNormAndLangAndActiveTrue("nghe bot", "vi"))
                .thenReturn(Optional.of(alias(102L, "nghe bot", ngheCanonical)));
        lenient().when(ingredientAliasRepository.findFirstByAliasTextNormAndLangAndActiveTrue("turmeric powder", "vi"))
                .thenReturn(Optional.of(alias(103L, "turmeric powder", ngheCanonical)));

        Meal canhCai = Meal.builder().id(99L).name("Canh cải thịt bằm").build();
        when(mealRepository.findAll()).thenReturn(List.of(canhCai));
        MealIngredient mi1 = MealIngredient.builder().id(1L).genericName("cải thìa").product(caiProduct).build();
        MealIngredient mi2 = MealIngredient.builder().id(2L).genericName("thịt heo xay").product(heoXayProduct).build();
        when(mealIngredientRepository.findByMealIdWithProduct(99L)).thenReturn(List.of(mi1, mi2));
    }

    @Test
    void shouldPassAllAcceptanceCases() {
        Map<String, Object> audit = auditService.runAcceptanceCases();

        Assertions.assertEquals(true, ((Map<?, ?>) audit.get("otHiemCase")).get("pass"));
        Assertions.assertEquals(true, ((Map<?, ?>) audit.get("botNgheCase")).get("pass"));
        Assertions.assertEquals(true, ((Map<?, ?>) audit.get("canhCaiCase")).get("pass"));
        Assertions.assertEquals(true, ((Map<?, ?>) audit.get("caChuaBridgeCase")).get("pass"));
    }

    private IngredientAlias alias(Long id, String norm, IngredientCanonical canonical) {
        return IngredientAlias.builder()
                .id(id)
                .aliasTextRaw(norm)
                .aliasTextNorm(norm)
                .lang("vi")
                .active(true)
                .canonical(canonical)
                .confidence(BigDecimal.ONE)
                .build();
    }

    private Product product(Long id, String name) {
        Category c = Category.builder().id(1L).name("Rau củ").categoryCode("RAU_CU").build();
        return Product.builder()
                .id(id)
                .name(name)
                .status("ACTIVE")
                .category(c)
                .build();
    }
}
