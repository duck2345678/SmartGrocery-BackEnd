package com.smartgrocery.backend.service.recommendation;

import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.VariantNutritionFact;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class FoodFamilyRules {
    private FoodFamilyRules() {
    }

    public static FoodFamilyProfile inferProfile(ProductVariant variant, VariantNutritionFact nutrition) {
        Product product = variant != null ? variant.getProduct() : null;
        String productName = normalizeText(product != null ? product.getName() : null);
        String categoryName = normalizeText(product != null && product.getCategory() != null ? product.getCategory().getName() : null);
        String variantName = normalizeText(variant != null ? variant.getVariantName() : null);
        String packageSize = normalizeText(variant != null ? variant.getPackageSize() : null);

        Set<FoodFamilyGroup> tags = new LinkedHashSet<>();
        FoodFamilyGroup primary = inferPrimaryFamily(productName, categoryName, variantName, packageSize, nutrition, tags);
        if (primary == null) {
            primary = FoodFamilyGroup.OTHER;
        }
        tags.add(primary);
        addSecondaryTags(productName, categoryName, variantName, packageSize, nutrition, tags);

        String rationale = buildRationale(primary, tags, productName, categoryName, nutrition);

        return FoodFamilyProfile.builder()
                .productId(product != null ? product.getId() : null)
                .variantId(variant != null ? variant.getId() : null)
                .sku(variant != null ? variant.getSku() : null)
                .productName(product != null ? product.getName() : null)
                .categoryName(product != null && product.getCategory() != null ? product.getCategory().getName() : null)
                .primaryFamily(primary)
                .familyTags(tags)
                .caloriesPer100g(nutrition != null ? nutrition.getCaloriesPer100g() : null)
                .proteinPer100g(nutrition != null ? nutrition.getProteinPer100g() : null)
                .carbsPer100g(nutrition != null ? nutrition.getCarbsPer100g() : null)
                .fatPer100g(nutrition != null ? nutrition.getFatPer100g() : null)
                .fiberPer100g(nutrition != null ? nutrition.getFiberPer100g() : null)
                .sugarPer100g(nutrition != null ? nutrition.getSugarPer100g() : null)
                .sodiumMgPer100g(nutrition != null ? nutrition.getSodiumMgPer100g() : null)
                .rationale(rationale)
                .build();
    }

    public static FoodFamilyGroup inferPrimaryFamily(
            String productName,
            String categoryName,
            String variantName,
            String packageSize,
            VariantNutritionFact nutrition,
            Set<FoodFamilyGroup> tags) {

        String blob = String.join(" ", safe(productName), safe(categoryName), safe(variantName), safe(packageSize));

        if (containsAny(blob, "rau", "củ", "cu", "salad", "xà lách", "xa lach", "nấm", "măng", "bắp cải", "cải")) {
            tags.add(FoodFamilyGroup.VEGETABLE);
            return FoodFamilyGroup.VEGETABLE;
        }
        if (containsAny(blob, "trái cây", "trai cay", "fruit", "chuối", "chuoi", "táo", "tao", "cam", "nho", "dưa hấu", "dua hau", "xoài", "xoai")) {
            tags.add(FoodFamilyGroup.FRUIT);
            return FoodFamilyGroup.FRUIT;
        }
        if (containsAny(blob, "sữa", "sua", "phô mai", "pho mai", "cheese", "yogurt", "yaourt", "bơ", "bo")) {
            tags.add(FoodFamilyGroup.DAIRY);
            return FoodFamilyGroup.DAIRY;
        }
        if (containsAny(blob, "cá", "ca ", "cá ", "tôm", "tom", "mực", "muc", "cua", "hải sản", "hai san", "seafood", "shrimp", "fish")) {
            tags.add(FoodFamilyGroup.SEAFOOD);
            return FoodFamilyGroup.SEAFOOD;
        }
        if (containsAny(blob, "thịt", "thit", "gà", "ga", "heo", "lợn", "lon", "bò", "bo", "trứng", "trung", "ức gà", "đùi gà", "canh ga", "cánh gà")) {
            tags.add(FoodFamilyGroup.PROTEIN);
            return FoodFamilyGroup.PROTEIN;
        }
        if (containsAny(blob, "cơm", "com", "phở", "pho", "bún", "bun", "mì", "mi", "bánh mì", "banh mi", "pasta", "noodle", "rice", "bột", "bot", "ngũ cốc", "ngu coc", "yến mạch", "yen mach")) {
            tags.add(FoodFamilyGroup.CARBOHYDRATE);
            return FoodFamilyGroup.CARBOHYDRATE;
        }
        if (containsAny(blob, "đậu", "dau", "legume", "đậu hũ", "dau hu", "đậu nành", "dau nanh", "đậu xanh", "dau xanh")) {
            tags.add(FoodFamilyGroup.LEGUME);
            return FoodFamilyGroup.LEGUME;
        }
        if (containsAny(blob, "dầu", "dau", "bơ", "bo", "mỡ", "mo", "hạt", "hat", "nuts", "seed", "olive")) {
            tags.add(FoodFamilyGroup.FAT);
            return FoodFamilyGroup.FAT;
        }
        if (containsAny(blob, "bánh", "banh", "kẹo", "keo", "snack", "chip", "crisps", "kem", "ice cream", "dessert", "tráng miệng", "trang mieng", "trà sữa", "tra sua", "milk tea")) {
            tags.add(FoodFamilyGroup.DESSERT);
            return FoodFamilyGroup.DESSERT;
        }
        if (containsAny(blob, "nước", "nuoc", "nước uống", "nuoc uong", "trà", "tra", "coffee", "cà phê", "ca phe", "juice", "soda", "sữa tươi", "sua tuoi")) {
            tags.add(FoodFamilyGroup.BEVERAGE);
            return FoodFamilyGroup.BEVERAGE;
        }

        if (nutrition != null) {
            if (isHighProtein(nutrition)) {
                tags.add(FoodFamilyGroup.PROTEIN);
                return FoodFamilyGroup.PROTEIN;
            }
            if (isHighCarbs(nutrition)) {
                tags.add(FoodFamilyGroup.CARBOHYDRATE);
                return FoodFamilyGroup.CARBOHYDRATE;
            }
            if (isHighFiber(nutrition)) {
                tags.add(FoodFamilyGroup.VEGETABLE);
                return FoodFamilyGroup.VEGETABLE;
            }
            if (isHighSugar(nutrition)) {
                tags.add(FoodFamilyGroup.DESSERT);
                return FoodFamilyGroup.DESSERT;
            }
        }

        return FoodFamilyGroup.OTHER;
    }

    public static void addSecondaryTags(
            String productName,
            String categoryName,
            String variantName,
            String packageSize,
            VariantNutritionFact nutrition,
            Set<FoodFamilyGroup> tags) {
        String blob = String.join(" ", safe(productName), safe(categoryName), safe(variantName), safe(packageSize));

        if (containsAny(blob, "chiên", "chien", "rán", "ran", "fried", "crispy", "tempura", "nugget")) {
            tags.add(FoodFamilyGroup.PROTEIN);
            tags.add(FoodFamilyGroup.SNACK);
        }
        if (containsAny(blob, "nướng", "nuong", "grilled", "roast", "baked")) {
            tags.add(FoodFamilyGroup.PROTEIN);
            tags.add(FoodFamilyGroup.CARBOHYDRATE);
        }
        if (containsAny(blob, "spicy", "cay", "cà ri", "ca ri", "curry")) {
            tags.add(FoodFamilyGroup.PROTEIN);
            tags.add(FoodFamilyGroup.CARBOHYDRATE);
        }
        if (containsAny(blob, "healthy", "eat clean", "low carb", "high protein", "giảm cân", "giam can")) {
            tags.add(FoodFamilyGroup.VEGETABLE);
            tags.add(FoodFamilyGroup.PROTEIN);
        }

        if (nutrition != null) {
            if (isHighProtein(nutrition)) tags.add(FoodFamilyGroup.PROTEIN);
            if (isHighCarbs(nutrition)) tags.add(FoodFamilyGroup.CARBOHYDRATE);
            if (isHighFat(nutrition)) tags.add(FoodFamilyGroup.FAT);
            if (isHighSugar(nutrition)) tags.add(FoodFamilyGroup.DESSERT);
            if (isHighSodium(nutrition)) tags.add(FoodFamilyGroup.CONDIMENT);
            if (isHighFiber(nutrition)) tags.add(FoodFamilyGroup.VEGETABLE);
            if (isLowCalorie(nutrition)) tags.add(FoodFamilyGroup.VEGETABLE);
        }
    }

    public static double familySimilarity(Set<FoodFamilyGroup> left, Set<FoodFamilyGroup> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return 0.0;
        Set<FoodFamilyGroup> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        Set<FoodFamilyGroup> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    public static double nutritionDistance(VariantNutritionFact source, VariantNutritionFact candidate) {
        if (source == null || candidate == null) return 0.5;
        double cal = normalizedDistance(source.getCaloriesPer100g(), candidate.getCaloriesPer100g(), 400.0);
        double protein = normalizedDistance(source.getProteinPer100g(), candidate.getProteinPer100g(), 30.0);
        double carbs = normalizedDistance(source.getCarbsPer100g(), candidate.getCarbsPer100g(), 60.0);
        double fat = normalizedDistance(source.getFatPer100g(), candidate.getFatPer100g(), 30.0);
        double fiber = normalizedDistance(source.getFiberPer100g(), candidate.getFiberPer100g(), 15.0);
        double sugar = normalizedDistance(source.getSugarPer100g(), candidate.getSugarPer100g(), 20.0);
        double sodium = normalizedDistance(source.getSodiumMgPer100g(), candidate.getSodiumMgPer100g(), 600.0);
        return (cal * 0.30) + (protein * 0.20) + (carbs * 0.15) + (fat * 0.15) + (fiber * 0.10) + (sugar * 0.05) + (sodium * 0.05);
    }

    public static double nutritionDistance(FoodFamilyProfile source, FoodFamilyProfile candidate) {
        if (source == null || candidate == null) return 0.5;
        double cal = normalizedDistance(source.getCaloriesPer100g(), candidate.getCaloriesPer100g(), 400.0);
        double protein = normalizedDistance(source.getProteinPer100g(), candidate.getProteinPer100g(), 30.0);
        double carbs = normalizedDistance(source.getCarbsPer100g(), candidate.getCarbsPer100g(), 60.0);
        double fat = normalizedDistance(source.getFatPer100g(), candidate.getFatPer100g(), 30.0);
        double fiber = normalizedDistance(source.getFiberPer100g(), candidate.getFiberPer100g(), 15.0);
        double sugar = normalizedDistance(source.getSugarPer100g(), candidate.getSugarPer100g(), 20.0);
        double sodium = normalizedDistance(source.getSodiumMgPer100g(), candidate.getSodiumMgPer100g(), 600.0);
        return (cal * 0.30) + (protein * 0.20) + (carbs * 0.15) + (fat * 0.15) + (fiber * 0.10) + (sugar * 0.05) + (sodium * 0.05);
    }

    public static boolean violatesAllergy(FoodFamilyProfile candidate, String allergies) {
        if (candidate == null || allergies == null || allergies.isBlank()) return false;
        String candidateText = String.join(" ", safe(candidate.getProductName()), safe(candidate.getCategoryName()), safe(candidate.getRationale())).toLowerCase(Locale.ROOT);
        for (String allergy : allergies.split(",")) {
            String a = normalizeText(allergy);
            if (!a.isBlank() && candidateText.contains(a)) {
                return true;
            }
        }
        return false;
    }

    public static boolean violatesDietPreference(FoodFamilyProfile candidate, String dietaryPreference) {
        if (candidate == null || dietaryPreference == null || dietaryPreference.isBlank()) return false;
        String normalized = normalizeText(dietaryPreference);
        if (normalized.contains("chay") || normalized.contains("vegetarian") || normalized.contains("vegan")) {
            return candidate.matchesFamily(FoodFamilyGroup.PROTEIN)
                    || candidate.matchesFamily(FoodFamilyGroup.SEAFOOD)
                    || containsAny(safe(candidate.getProductName()) + " " + safe(candidate.getCategoryName()), "thịt", "thit", "cá", "ca ", "tôm", "tom", "mực", "muc", "trứng", "trung", "sữa", "sua");
        }
        return false;
    }

    public static boolean satisfiesCalorieBand(VariantNutritionFact source, VariantNutritionFact candidate) {
        if (source == null || candidate == null || source.getCaloriesPer100g() == null || candidate.getCaloriesPer100g() == null) {
            return true;
        }
        BigDecimal sourceCalories = source.getCaloriesPer100g();
        BigDecimal candidateCalories = candidate.getCaloriesPer100g();
        BigDecimal maxAllowed = sourceCalories.multiply(BigDecimal.valueOf(1.15));
        BigDecimal minAllowed = sourceCalories.multiply(BigDecimal.valueOf(0.75));
        return candidateCalories.compareTo(maxAllowed) <= 0 && candidateCalories.compareTo(minAllowed) >= 0;
    }

    public static boolean satisfiesCalorieBand(FoodFamilyProfile source, FoodFamilyProfile candidate) {
        if (source == null || candidate == null || source.getCaloriesPer100g() == null || candidate.getCaloriesPer100g() == null) {
            return true;
        }
        BigDecimal sourceCalories = source.getCaloriesPer100g();
        BigDecimal candidateCalories = candidate.getCaloriesPer100g();
        BigDecimal maxAllowed = sourceCalories.multiply(BigDecimal.valueOf(1.15));
        BigDecimal minAllowed = sourceCalories.multiply(BigDecimal.valueOf(0.75));
        return candidateCalories.compareTo(maxAllowed) <= 0 && candidateCalories.compareTo(minAllowed) >= 0;
    }

    public static String buildReason(FoodFamilyProfile source, FoodFamilyProfile candidate, double graphDistance, double nutritionDistance, int sharedTags) {
        List<String> reasons = new ArrayList<>();
        if (source != null && candidate != null) {
            reasons.add("cùng họ " + Optional.ofNullable(candidate.getPrimaryFamily()).orElse(FoodFamilyGroup.OTHER).name().toLowerCase(Locale.ROOT));
        }
        if (sharedTags > 0) {
            reasons.add("chia sẻ " + sharedTags + " nhánh họ thực phẩm");
        }
        if (nutritionDistance <= 0.25) {
            reasons.add("dinh dưỡng gần với món gốc");
        } else if (nutritionDistance <= 0.5) {
            reasons.add("dinh dưỡng tương đối gần");
        }
        if (graphDistance <= 1.5) {
            reasons.add("khoảng cách đồ thị ngắn");
        }
        if (reasons.isEmpty()) {
            reasons.add("gần nhất trong nhóm họ thực phẩm phù hợp");
        }
        return String.join(", ", reasons);
    }

    private static String buildRationale(FoodFamilyGroup primary, Set<FoodFamilyGroup> tags, String productName, String categoryName, VariantNutritionFact nutrition) {
        StringBuilder sb = new StringBuilder();
        sb.append("Phân loại là ").append(primary != null ? primary.name().toLowerCase(Locale.ROOT) : "chưa xác định");
        if (tags != null && tags.size() > 1) {
            sb.append(" với các đặc tính: ");
            String tagList = tags.stream()
                    .filter(t -> t != primary)
                    .map(t -> t.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(", "));
            sb.append(tagList);
        }
        if (nutrition != null) {
            List<String> nutrients = new ArrayList<>();
            if (isHighProtein(nutrition)) nutrients.add("giàu đạm");
            if (isHighFiber(nutrition)) nutrients.add("nhiều chất xơ");
            if (isHighSugar(nutrition)) nutrients.add("nhiều đường");
            if (isHighSodium(nutrition)) nutrients.add("nhiều muối");
            if (!nutrients.isEmpty()) {
                sb.append(". Đặc điểm dinh dưỡng: ").append(String.join(", ", nutrients));
            }
        }
        return sb.toString();
    }

    public static String normalizeText(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.replaceAll("[^a-zA-Z0-9àáảãạâầấẩẫậăằắẳẵặèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsAny(String text, String... tokens) {
        if (text == null || text.isBlank()) return false;
        String normalized = normalizeText(text);
        return Arrays.stream(tokens)
                .map(FoodFamilyRules::normalizeText)
                .filter(token -> !token.isBlank())
                .anyMatch(normalized::contains);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isHighProtein(VariantNutritionFact nf) {
        return nf != null && nf.getProteinPer100g() != null && nf.getProteinPer100g().compareTo(BigDecimal.valueOf(15)) >= 0;
    }

    private static boolean isHighCarbs(VariantNutritionFact nf) {
        return nf != null && nf.getCarbsPer100g() != null && nf.getCarbsPer100g().compareTo(BigDecimal.valueOf(20)) >= 0;
    }

    private static boolean isHighFat(VariantNutritionFact nf) {
        return nf != null && nf.getFatPer100g() != null && nf.getFatPer100g().compareTo(BigDecimal.valueOf(17.5)) > 0;
    }

    private static boolean isHighSugar(VariantNutritionFact nf) {
        return nf != null && nf.getSugarPer100g() != null && nf.getSugarPer100g().compareTo(BigDecimal.valueOf(10)) > 0;
    }

    private static boolean isHighSodium(VariantNutritionFact nf) {
        return nf != null && nf.getSodiumMgPer100g() != null && nf.getSodiumMgPer100g().compareTo(BigDecimal.valueOf(600)) > 0;
    }

    private static boolean isHighFiber(VariantNutritionFact nf) {
        return nf != null && nf.getFiberPer100g() != null && nf.getFiberPer100g().compareTo(BigDecimal.valueOf(3)) >= 0;
    }

    private static boolean isLowCalorie(VariantNutritionFact nf) {
        return nf != null && nf.getCaloriesPer100g() != null && nf.getCaloriesPer100g().compareTo(BigDecimal.valueOf(120)) <= 0;
    }

    private static double normalizedDistance(BigDecimal left, BigDecimal right, double range) {
        if (left == null || right == null) return 0.5;
        double diff = Math.abs(left.doubleValue() - right.doubleValue());
        return Math.min(1.0, diff / Math.max(1.0, range));
    }
}
