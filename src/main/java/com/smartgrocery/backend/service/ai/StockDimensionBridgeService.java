package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.IngredientCanonical;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.entity.UnitCanonical;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class StockDimensionBridgeService {

    public BridgeResult bridgeRequiredAmountToMass(MealIngredient ingredient) {
        if (ingredient == null || ingredient.getQuantityValue() == null) {
            return BridgeResult.unresolved("NO_REQUIRED_QUANTITY");
        }
        UnitCanonical unit = ingredient.getQuantityUnitCanonical();
        if (unit == null || unit.getDimension() == null) {
            return BridgeResult.unresolved("NO_REQUIRED_UNIT");
        }
        String requiredDim = unit.getDimension().toLowerCase(Locale.ROOT);
        if (requiredDim.equals("mass")) {
            BigDecimal factor = unit.getToBaseFactor() == null ? BigDecimal.ONE : unit.getToBaseFactor();
            return BridgeResult.bridged(ingredient.getQuantityValue().multiply(factor), "DIRECT_MASS");
        }
        if (!requiredDim.equals("count")) {
            return BridgeResult.unresolved("DIMENSION_NOT_SUPPORTED_" + requiredDim);
        }

        IngredientCanonical canonical = ingredient.getCanonicalIngredient();
        if (canonical == null || canonical.getAverageWeightPerUnitG() == null) {
            return BridgeResult.unresolved("MISSING_AVG_WEIGHT");
        }
        BigDecimal requiredMassG = ingredient.getQuantityValue().multiply(canonical.getAverageWeightPerUnitG())
                .setScale(4, RoundingMode.HALF_UP);
        return BridgeResult.bridged(requiredMassG, "DIMENSION_BRIDGED");
    }

    public record BridgeResult(
            String status,
            BigDecimal requiredMassG,
            String reasonCode
    ) {
        public static BridgeResult bridged(BigDecimal requiredMassG, String reason) {
            return new BridgeResult("BRIDGED", requiredMassG, reason);
        }

        public static BridgeResult unresolved(String reason) {
            return new BridgeResult("UNRESOLVED", null, reason);
        }
    }
}
