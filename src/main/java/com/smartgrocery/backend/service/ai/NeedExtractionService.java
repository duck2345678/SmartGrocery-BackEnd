package com.smartgrocery.backend.service.ai;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;

@Service
public class NeedExtractionService {

    public enum Need {
        FOOD_MEAL,
        DRINK,
        SNACK_SWEET,
        HOUSEHOLD_CLEANING,
        DISHWASHING,
        LAUNDRY,
        DIRECT_PRODUCT,
        DIRECT_RECIPE
    }

    public enum Constraint {
        LOW_BUDGET,
        HOT_WEATHER,
        THIRSTY,
        HUNGRY
    }

    public record NeedAnalysis(
            String normalizedText,
            Set<Need> needs,
            Set<Constraint> constraints,
            List<String> directProductTerms,
            Optional<String> recipeKey,
            String mealGoalSignature
    ) {
        public boolean hasNeed(Need need) {
            return needs.contains(need);
        }

        public boolean hasConstraint(Constraint constraint) {
            return constraints.contains(constraint);
        }
    }

    private static final List<String> DIRECT_PRODUCT_TERMS = List.of(
            "ca phe",
            "sua hanh nhan",
            "dau an",
            "hat nem",
            "nuoc giat",
            "nuoc rua chen"
    );

    public NeedAnalysis analyze(String userMessage) {
        String n = normalizeText(userMessage);
        EnumSet<Need> needs = EnumSet.noneOf(Need.class);
        EnumSet<Constraint> constraints = EnumSet.noneOf(Constraint.class);

        List<String> directProductTerms = extractDirectProductTerms(n);
        if (!directProductTerms.isEmpty()) {
            needs.add(Need.DIRECT_PRODUCT);
        }

        Optional<String> recipeKey = recipeKey(userMessage);
        recipeKey.ifPresent(key -> needs.add(Need.DIRECT_RECIPE));

        if (isMealOrDietIntent(userMessage) || isCoffeeMealPairingIntent(userMessage) || n.contains("doi bung")) {
            needs.add(Need.FOOD_MEAL);
        }
        if (n.contains("khat") || n.contains("nuoc") || n.contains("giai khat") || n.contains("nong qua")) {
            needs.add(Need.DRINK);
        }
        if (n.contains("them ngot") || n.contains("do ngot") || n.contains("banh keo") || n.contains("snack")) {
            needs.add(Need.SNACK_SWEET);
        }
        if (n.contains("nha do") || n.contains("nha ban") || n.contains("lau nha")
                || n.contains("don nha") || n.contains("ve sinh")) {
            needs.add(Need.HOUSEHOLD_CLEANING);
        }
        if (n.contains("nuoc rua chen") || n.contains("rua chen") || n.contains("rua bat")) {
            needs.add(Need.DISHWASHING);
        }
        if (n.contains("nuoc giat") || n.contains("giat do") || n.contains("giat quan ao")) {
            needs.add(Need.LAUNDRY);
        }

        if (n.contains("cuoi thang") || n.contains("het tien") || n.contains("it tien")
                || n.contains("re thoi") || n.contains("tiet kiem")) {
            constraints.add(Constraint.LOW_BUDGET);
        }
        if (n.contains("troi nong") || n.contains("nong qua") || n.contains("nong buc")) {
            constraints.add(Constraint.HOT_WEATHER);
        }
        if (n.contains("khat")) {
            constraints.add(Constraint.THIRSTY);
        }
        if (n.contains("doi bung") || n.contains("do bung")) {
            constraints.add(Constraint.HUNGRY);
        }

        return new NeedAnalysis(
                n,
                Collections.unmodifiableSet(needs),
                Collections.unmodifiableSet(constraints),
                directProductTerms,
                recipeKey,
                mealGoalSignature(userMessage)
        );
    }

    public boolean isShoppingListRequest(String userMessage) {
        String n = normalizeText(userMessage);
        return n.contains("tao danh sach")
                || n.contains("lap danh sach")
                || n.contains("chot danh sach")
                || n.contains("danh sach mua")
                || n.contains("shopping list")
                || n.contains("list mua")
                || n.contains("mua sam")
                || n.contains("mua do")
                || n.contains("mua nguyen lieu")
                || n.contains("them vao gio")
                || n.contains("bo vao gio")
                || n.contains("cho vao gio")
                || n.contains("them tat ca")
                || n.contains("chot mon")
                || n.contains("chot bua");
    }

    public boolean isMealOrDietIntent(String userMessage) {
        String n = normalizeText(userMessage);
        return n.contains("bua toi") || n.contains("bua sang") || n.contains("bua trua")
                || n.contains("an toi") || n.contains("an sang") || n.contains("an trua")
                || n.contains("giam can") || n.contains("tang can") || n.contains("healthy")
                || n.contains("diet") || n.contains("protein") || n.contains("dinh duong")
                || n.contains("thuc don") || n.contains("meal") || n.contains("nau")
                || n.contains("mon an") || n.contains("cong thuc") || n.contains("recipe")
                || n.contains("no lau") || n.contains("nhe nhang") || n.contains("khong ngay")
                || n.contains("beefsteak") || n.contains("steak")
                || n.contains("mi y") || n.contains("spaghetti") || n.contains("pasta")
                || n.contains("an kieng") || n.contains("it calo") || n.contains("low calorie");
    }

    public boolean isCoffeeMealPairingIntent(String userMessage) {
        String n = normalizeText(userMessage);
        boolean mentionsCoffee = containsNormalizedPhrase(n, "ca phe")
                || containsNormalizedPhrase(n, "coffee");
        if (!mentionsCoffee) {
            return false;
        }
        return n.contains("goi y")
                || n.contains("an gi")
                || n.contains("mon")
                || n.contains("thuc don")
                || n.contains("di kem")
                || n.contains("hop voi")
                || n.contains("uong voi");
    }

    public String mealGoalSignature(String userMessage) {
        String n = normalizeText(userMessage);
        if (containsNormalizedPhrase(n, "ca phe") || containsNormalizedPhrase(n, "coffee")) {
            return "MEAL_WITH_COFFEE";
        }
        if (n.contains("bua sang") || n.contains("an sang")) {
            return "BREAKFAST";
        }
        if (n.contains("bua toi") || n.contains("an toi")) {
            return "DINNER";
        }
        if (n.contains("an chay")) {
            return "VEGETARIAN";
        }
        if (n.contains("healthy") || n.contains("giam can") || n.contains("it calo")) {
            return "HEALTHY";
        }
        return n;
    }

    public List<String> extractDirectProductTerms(String normalizedMessage) {
        String n = normalizeText(normalizedMessage);
        if (n.isBlank()) {
            return List.of();
        }
        return DIRECT_PRODUCT_TERMS.stream()
                .filter(term -> containsNormalizedPhrase(n, term))
                .sorted(Comparator.comparingInt(n::indexOf))
                .distinct()
                .toList();
    }

    public Optional<String> recipeKey(String userMessage) {
        String n = normalizeText(userMessage);
        if (containsNormalizedPhrase(n, "ga kho")) {
            return Optional.of("GA_KHO");
        }
        if (containsNormalizedPhrase(n, "salad healthy")
                || (containsNormalizedPhrase(n, "salad") && containsNormalizedPhrase(n, "healthy"))) {
            return Optional.of("SALAD_HEALTHY");
        }
        if (containsNormalizedPhrase(n, "mi y")
                || containsNormalizedPhrase(n, "my y")
                || containsNormalizedPhrase(n, "spaghetti")
                || containsNormalizedPhrase(n, "pasta")) {
            return Optional.of("MI_Y");
        }
        return Optional.empty();
    }

    public boolean containsNormalizedPhrase(String normalizedText, String phrase) {
        String text = normalizeText(normalizedText);
        String normalizedPhrase = normalizeText(phrase);
        if (text.isBlank() || normalizedPhrase.isBlank()) {
            return false;
        }
        return (" " + text + " ").contains(" " + normalizedPhrase + " ");
    }

    public String normalizeText(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
