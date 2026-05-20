package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.IngredientCanonical;
import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.UnitCanonical;
import com.smartgrocery.backend.repository.jpa.MealIngredientRepository;
import com.smartgrocery.backend.repository.jpa.MealRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcceptanceCaseAuditService {

    private final IngredientMatchV2Service ingredientMatchV2Service;
    private final MealRepository mealRepository;
    private final MealIngredientRepository mealIngredientRepository;
    private final ProductRepository productRepository;
    private final StockDimensionBridgeService stockDimensionBridgeService;

    public Map<String, Object> runAcceptanceCases() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("otHiemCase", caseOtHiemNotCherry());
        result.put("botNgheCase", caseBotNgheCanonical());
        result.put("canhCaiCase", caseCanhCaiHasRauCai());
        result.put("caChuaBridgeCase", caseCaChuaBridgeCountToMass());
        return result;
    }

    private Map<String, Object> caseOtHiemNotCherry() {
        Set<Long> stockedIds = productRepository.findActiveWithCategory().stream()
                .map(Product::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        IngredientMatchV2Service.MatchDecision d = ingredientMatchV2Service.match("ớt hiểm trái", stockedIds);
        boolean pass = !"MATCHED".equals(d.status())
                || (d.matchedProduct() != null && !normalize(d.matchedProduct().getName()).contains("cherry"));
        return Map.of(
                "pass", pass,
                "status", d.status(),
                "matchedProduct", d.matchedProduct() == null ? null : d.matchedProduct().getName(),
                "reason", d.reasonCode()
        );
    }

    private Map<String, Object> caseBotNgheCanonical() {
        Set<Long> stockedIds = productRepository.findActiveWithCategory().stream()
                .map(Product::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        IngredientMatchV2Service.MatchDecision d1 = ingredientMatchV2Service.match("bột nghệ", stockedIds);
        IngredientMatchV2Service.MatchDecision d2 = ingredientMatchV2Service.match("nghệ bột", stockedIds);
        IngredientMatchV2Service.MatchDecision d3 = ingredientMatchV2Service.match("bột nghệ", stockedIds);

        String c1 = d1.canonical() == null ? null : d1.canonical().getCanonicalCode();
        String c2 = d2.canonical() == null ? null : d2.canonical().getCanonicalCode();
        String c3 = d3.canonical() == null ? null : d3.canonical().getCanonicalCode();
        boolean pass = c1 != null && c1.equals(c2) && c1.equals(c3);
        return Map.of(
                "pass", pass,
                "canonicalCodes", java.util.Arrays.asList(c1, c2, c3)
        );
    }

    private Map<String, Object> caseCanhCaiHasRauCai() {
        Optional<Meal> mealOpt = mealRepository.findAll().stream()
                .filter(m -> normalize(m.getName()).contains("canh cai") && normalize(m.getName()).contains("thit bam"))
                .findFirst();
        if (mealOpt.isEmpty()) {
            return Map.of("pass", false, "reason", "MEAL_NOT_FOUND");
        }
        List<MealIngredient> ingredients = mealIngredientRepository.findByMealIdWithProduct(mealOpt.get().getId());
        boolean hasRauCai = ingredients.stream().anyMatch(mi -> {
            String n = normalize(mi.getGenericName() != null ? mi.getGenericName() :
                    (mi.getProduct() != null ? mi.getProduct().getName() : ""));
            return n.contains("cai") || n.contains("rau cai");
        });
        return Map.of(
                "pass", hasRauCai,
                "mealId", mealOpt.get().getId(),
                "mealName", mealOpt.get().getName()
        );
    }

    private Map<String, Object> caseCaChuaBridgeCountToMass() {
        MealIngredient ingredient = new MealIngredient();
        IngredientCanonical canonical = new IngredientCanonical();
        canonical.setCanonicalCode("ca_chua");
        canonical.setAverageWeightPerUnitG(new BigDecimal("150"));
        ingredient.setCanonicalIngredient(canonical);
        ingredient.setQuantityValue(new BigDecimal("2"));
        UnitCanonical unit = new UnitCanonical();
        unit.setDimension("count");
        unit.setToBaseFactor(BigDecimal.ONE);
        ingredient.setQuantityUnitCanonical(unit);

        StockDimensionBridgeService.BridgeResult bridge = stockDimensionBridgeService.bridgeRequiredAmountToMass(ingredient);
        boolean pass = "BRIDGED".equals(bridge.status()) && bridge.requiredMassG() != null
                && bridge.requiredMassG().compareTo(new BigDecimal("300.0000")) == 0;
        return Map.of(
                "pass", pass,
                "status", bridge.status(),
                "requiredMassG", bridge.requiredMassG(),
                "reason", bridge.reasonCode()
        );
    }

    private String normalize(String text) {
        if (text == null) return "";
        String n = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replace('\u0111', 'd').replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }
}
