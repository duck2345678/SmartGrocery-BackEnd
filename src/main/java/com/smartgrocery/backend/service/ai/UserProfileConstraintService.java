package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.UserNutritionProfile;
import com.smartgrocery.backend.repository.jpa.UserNutritionProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileConstraintService {

    private final UserNutritionProfileRepository nutritionProfileRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BLOCK_INGREDIENTS = "blockIngredients";
    private static final String ALLOW_INGREDIENTS = "allowIngredients";
    private static final String AVOID_METHODS = "avoidMethods";
    private static final String PREFERENCES = "preferences";

    private static final Map<String, List<String>> SEMANTIC_AVOIDANCE_EXPANSIONS = Map.of(
            "ca chua", List.of("ca chua", "tomato", "sot ca chua", "tuong ca", "ketchup"),
            "khong an cay", List.of("cay", "ot", "tuong ot", "sa te", "spicy", "chili", "pepper sauce"),
            "khong an do chien", List.of("chien", "ran", "chien ran", "deep fried", "fried"),
            "khong an luoc", List.of("luoc", "boiled")
    );

    public Set<String> loadAvoidanceTerms(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        try {
            return nutritionProfileRepository.findByUser_Id(userId)
                    .map(this::extractProfileAvoidanceTerms)
                    .orElse(Set.of());
        } catch (Exception e) {
            log.warn("Could not load profile constraints for user {}: {}", userId, e.getMessage());
            return Set.of();
        }
    }

    public Set<String> extractAvoidanceTerms(String rawProfileText) {
        if (rawProfileText == null || rawProfileText.isBlank()) {
            return Set.of();
        }
        List<String> normalizedTokens = Arrays.stream(rawProfileText.split("[,;/\\n]+"))
                .map(this::normalizeText)
                .filter(token -> token.length() >= 2)
                .filter(token -> !isNegatedClearance(token))
                .filter(token -> !isAllergyDenial(token))
                .toList();
        String normalizedProfileText = String.join(" ", normalizedTokens);
        LinkedHashSet<String> terms = new LinkedHashSet<>(normalizedTokens);

        for (Map.Entry<String, List<String>> entry : SEMANTIC_AVOIDANCE_EXPANSIONS.entrySet()) {
            if (containsNormalizedPhrase(normalizedProfileText, entry.getKey())
                    || entry.getValue().stream().anyMatch(alias -> containsNormalizedPhrase(normalizedProfileText, alias))) {
                terms.addAll(entry.getValue());
            }
        }

        if (containsAnyPhrase(normalizedProfileText, List.of("khong cay", "so cay", "it cay", "tranh cay"))) {
            terms.addAll(SEMANTIC_AVOIDANCE_EXPANSIONS.get("khong an cay"));
        }
        if (containsAnyPhrase(normalizedProfileText, List.of("khong an chien ran", "khong chien ran", "tranh do chien", "han che do chien"))) {
            terms.addAll(SEMANTIC_AVOIDANCE_EXPANSIONS.get("khong an do chien"));
        }
        if (containsAnyPhrase(normalizedProfileText, List.of("khong luoc", "tranh mon luoc"))) {
            terms.addAll(SEMANTIC_AVOIDANCE_EXPANSIONS.get("khong an luoc"));
        }

        return terms;
    }

    public Set<String> extractProfileAvoidanceTerms(UserNutritionProfile profile) {
        if (profile == null) {
            return Set.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        FoodConstraintTerms structured = readFoodConstraintTerms(profile.getFoodConstraints());
        structured.blocked().forEach(term -> addTermWithSemanticExpansion(term, terms));
        structured.avoidMethods().forEach(term -> addTermWithSemanticExpansion(term, terms));
        terms.addAll(extractAvoidanceTerms(profile.getAllergies()));
        terms.addAll(extractExplicitAvoidanceTerms(profile.getDietaryPreference()));
        terms.addAll(extractExplicitAvoidanceTerms(profile.getHealthGoals()));
        structured.allowed().forEach(allowed -> terms.removeIf(term -> sameFoodTerm(term, allowed)));
        return terms;
    }

    public String mergeFoodConstraints(
            String currentJson,
            Collection<String> blockIngredients,
            Collection<String> allowIngredients,
            Collection<String> avoidMethods,
            Collection<String> preferences
    ) {
        FoodConstraintTerms current = readFoodConstraintTerms(currentJson);
        LinkedHashSet<String> blocked = new LinkedHashSet<>(current.blocked());
        LinkedHashSet<String> allowed = new LinkedHashSet<>(current.allowed());
        LinkedHashSet<String> methods = new LinkedHashSet<>(current.avoidMethods());
        LinkedHashSet<String> prefs = new LinkedHashSet<>(current.preferences());

        addCleanTerms(blocked, blockIngredients);
        addCleanTerms(allowed, allowIngredients);
        addCleanTerms(methods, avoidMethods);
        addCleanTerms(prefs, preferences);

        allowed.forEach(allowedTerm -> blocked.removeIf(blockedTerm -> sameFoodTerm(blockedTerm, allowedTerm)));

        ObjectNode root = objectMapper.createObjectNode();
        root.set(BLOCK_INGREDIENTS, toArrayNode(blocked));
        root.set(ALLOW_INGREDIENTS, toArrayNode(allowed));
        root.set(AVOID_METHODS, toArrayNode(methods));
        root.set(PREFERENCES, toArrayNode(prefs));
        return root.toString();
    }

    public Set<String> extractClearedAllergyTerms(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> cleared = new LinkedHashSet<>();
        for (String rawToken : rawText.split("[,;/\\n]+")) {
            String token = normalizeText(rawToken);
            if (token.isBlank()) {
                continue;
            }
            if (isClearAllAllergies(token)) {
                cleared.add("*");
                continue;
            }
            String clearedTerm = extractDeniedAllergyTerm(token);
            if (!clearedTerm.isBlank()) {
                cleared.add(clearedTerm);
            }
        }
        return cleared;
    }

    public String removeClearedAllergyTerms(String currentAllergies, String correctionText) {
        Set<String> clearedTerms = extractClearedAllergyTerms(correctionText);
        if (clearedTerms.isEmpty()) {
            return currentAllergies;
        }
        if (clearedTerms.contains("*") || currentAllergies == null || currentAllergies.isBlank()) {
            return "";
        }
        List<String> remaining = Arrays.stream(currentAllergies.split("[,;/\\n]+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .filter(token -> !matchesAnyClearedTerm(token, clearedTerms))
                .toList();
        return String.join(", ", remaining);
    }

    public boolean violatesProduct(Product product, Set<String> avoidanceTerms) {
        if (product == null) {
            return true;
        }
        return violatesText(productText(product), avoidanceTerms);
    }

    public boolean violatesText(String text, Set<String> avoidanceTerms) {
        String normalizedText = normalizeText(text);
        if (normalizedText.isBlank() || avoidanceTerms == null || avoidanceTerms.isEmpty()) {
            return false;
        }
        return avoidanceTerms.stream()
                .filter(term -> term != null && !term.isBlank())
                .map(this::normalizeText)
                .anyMatch(term -> containsNormalizedPhrase(normalizedText, term) || normalizedText.contains(term));
    }

    private String toRawProfileText(UserNutritionProfile profile) {
        return String.join("\n",
                profile.getAllergies() != null ? profile.getAllergies() : "",
                profile.getDietaryPreference() != null ? profile.getDietaryPreference() : "",
                profile.getHealthGoals() != null ? profile.getHealthGoals() : "");
    }

    private String productText(Product product) {
        return String.join(" ",
                product.getName() != null ? product.getName() : "",
                product.getShortDescription() != null ? product.getShortDescription() : "",
                product.getDescription() != null ? product.getDescription() : "",
                product.getCategory() != null && product.getCategory().getName() != null
                        ? product.getCategory().getName()
                        : "");
    }

    private FoodConstraintTerms readFoodConstraintTerms(String json) {
        if (json == null || json.isBlank()) {
            return new FoodConstraintTerms(List.of(), List.of(), List.of(), List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return new FoodConstraintTerms(
                    readStringArray(root.path(BLOCK_INGREDIENTS)),
                    readStringArray(root.path(ALLOW_INGREDIENTS)),
                    readStringArray(root.path(AVOID_METHODS)),
                    readStringArray(root.path(PREFERENCES))
            );
        } catch (Exception e) {
            log.warn("Could not parse food constraints JSON: {}", e.getMessage());
            return new FoodConstraintTerms(List.of(), List.of(), List.of(), List.of());
        }
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        });
        return values;
    }

    private void addCleanTerms(Set<String> target, Collection<String> terms) {
        if (terms == null) {
            return;
        }
        terms.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .forEach(term -> {
                    target.removeIf(existing -> sameFoodTerm(existing, term));
                    target.add(term);
                });
    }

    private ArrayNode toArrayNode(Collection<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(array::add);
        return array;
    }

    private boolean sameFoodTerm(String left, String right) {
        String normalizedLeft = normalizeText(left);
        String normalizedRight = normalizeText(right);
        return !normalizedLeft.isBlank() && normalizedLeft.equals(normalizedRight);
    }

    private boolean isNegatedClearance(String token) {
        return token.contains("het di ung")
                || token.contains("khong con di ung")
                || token.contains("da het di ung");
    }

    private boolean isAllergyDenial(String token) {
        return isClearAllAllergies(token) || !extractDeniedAllergyTerm(token).isBlank();
    }

    private boolean isClearAllAllergies(String token) {
        return token.contains("khong di ung gi")
                || token.contains("khong bi di ung gi")
                || token.contains("khong co di ung gi")
                || token.contains("khong con di ung gi")
                || token.equals("khong di ung")
                || token.equals("khong bi di ung")
                || token.equals("khong co di ung");
    }

    private String extractDeniedAllergyTerm(String token) {
        for (String prefix : List.of(
                "toi khong bi di ung voi",
                "minh khong bi di ung voi",
                "toi khong co di ung voi",
                "minh khong co di ung voi",
                "toi khong di ung voi",
                "minh khong di ung voi",
                "khong bi di ung voi",
                "khong co di ung voi",
                "khong di ung voi",
                "toi khong bi di ung",
                "minh khong bi di ung",
                "toi khong co di ung",
                "minh khong co di ung",
                "toi khong di ung",
                "minh khong di ung",
                "khong bi di ung",
                "khong co di ung",
                "khong di ung",
                "khong phai di ung")) {
            if (containsNormalizedPhrase(token, prefix)) {
                String term = (" " + token + " ").replaceFirst(".*\\b" + java.util.regex.Pattern.quote(prefix) + "\\b", "").trim();
                return stripFillerWords(term);
            }
        }
        return "";
    }

    private boolean matchesAnyClearedTerm(String allergyToken, Set<String> clearedTerms) {
        String normalizedAllergy = normalizeText(allergyToken);
        return clearedTerms.stream()
                .map(this::normalizeText)
                .anyMatch(cleared -> !cleared.isBlank()
                        && (containsNormalizedPhrase(normalizedAllergy, cleared)
                        || containsNormalizedPhrase(cleared, normalizedAllergy)));
    }

    private String stripFillerWords(String text) {
        String stripped = normalizeText(text)
                .replaceAll("\\b(voi|nua|dau|nhe|nha|toi|minh|nhung)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return stripped;
    }

    private Set<String> extractExplicitAvoidanceTerms(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Set.of();
        }
        String normalizedText = normalizeText(rawText);
        if (normalizedText.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        addExplicitSemanticAvoidanceTerms(normalizedText, terms);

        for (String rawToken : rawText.split("[,;/\\n]+")) {
            String token = normalizeText(rawToken);
            if (token.length() < 2 || isNegatedClearance(token) || isAllergyDenial(token)) {
                continue;
            }
            extractTermAfterAvoidancePrefix(token).ifPresent(term -> addTermWithSemanticExpansion(term, terms));
        }
        return terms;
    }

    private void addExplicitSemanticAvoidanceTerms(String normalizedText, Set<String> terms) {
        if (containsAnyPhrase(normalizedText, List.of("khong cay", "so cay", "it cay", "tranh cay"))) {
            terms.addAll(SEMANTIC_AVOIDANCE_EXPANSIONS.get("khong an cay"));
        }
        if (containsAnyPhrase(normalizedText, List.of("khong an chien ran", "khong chien ran", "tranh do chien", "han che do chien"))) {
            terms.addAll(SEMANTIC_AVOIDANCE_EXPANSIONS.get("khong an do chien"));
        }
        if (containsAnyPhrase(normalizedText, List.of("khong luoc", "tranh mon luoc"))) {
            terms.addAll(SEMANTIC_AVOIDANCE_EXPANSIONS.get("khong an luoc"));
        }
    }

    private void addTermWithSemanticExpansion(String term, Set<String> terms) {
        String normalizedTerm = normalizeText(term);
        if (normalizedTerm.isBlank()) {
            return;
        }
        terms.add(normalizedTerm);
        for (Map.Entry<String, List<String>> entry : SEMANTIC_AVOIDANCE_EXPANSIONS.entrySet()) {
            if (containsNormalizedPhrase(normalizedTerm, entry.getKey())
                    || entry.getValue().stream().anyMatch(alias -> containsNormalizedPhrase(normalizedTerm, alias))) {
                terms.addAll(entry.getValue());
            }
        }
    }

    private Optional<String> extractTermAfterAvoidancePrefix(String token) {
        for (String prefix : List.of(
                "khong an",
                "khong uong",
                "khong dung",
                "tranh",
                "han che",
                "kieng")) {
            if (containsNormalizedPhrase(token, prefix)) {
                String term = (" " + token + " ").replaceFirst(".*\\b" + java.util.regex.Pattern.quote(prefix) + "\\b", "").trim();
                term = stripFillerWords(term);
                if (!term.isBlank()) {
                    return Optional.of(term);
                }
            }
        }
        return Optional.empty();
    }

    private boolean containsAnyPhrase(String normalizedText, List<String> phrases) {
        return phrases.stream().anyMatch(phrase -> containsNormalizedPhrase(normalizedText, phrase));
    }

    private boolean containsNormalizedPhrase(String normalizedText, String phrase) {
        String text = normalizeText(normalizedText);
        String normalizedPhrase = normalizeText(phrase);
        if (text.isBlank() || normalizedPhrase.isBlank()) {
            return false;
        }
        return (" " + text + " ").contains(" " + normalizedPhrase + " ");
    }

    private String normalizeText(String text) {
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

    private record FoodConstraintTerms(
            List<String> blocked,
            List<String> allowed,
            List<String> avoidMethods,
            List<String> preferences
    ) {}
}
