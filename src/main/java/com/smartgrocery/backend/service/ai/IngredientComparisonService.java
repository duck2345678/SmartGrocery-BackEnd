package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientComparisonService {

    private final ProductNodeRepository productNodeRepository;

    public record IngredientMatchResult(
            String originalIngredient,
            Long productId,
            String productName,
            double confidence,
            String status, // MATCHED, MISSING, AMBIGUOUS
            String details
    ) {}

    public List<IngredientMatchResult> analyzeAndMatchIngredients(List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        log.info("Starting automated ingredient comparison for {} items", ingredients.size());
        List<IngredientMatchResult> results = new ArrayList<>();

        for (String ing : ingredients) {
            results.add(matchSingleIngredient(ing));
        }

        return results;
    }

    private IngredientMatchResult matchSingleIngredient(String ingredient) {
        String query = normalizeText(ingredient);
        if (query.isBlank()) {
            return new IngredientMatchResult(ingredient, null, null, 0.0, "MISSING", "Nguyên liệu trống.");
        }

        List<String> aliases = expandAliases(query);

        // 1. Try Exact/Synonym match in Neo4j
        List<ProductNode> candidates = new ArrayList<>();
        for (String alias : aliases) {
            List<ProductNode> aliasCandidates = productNodeRepository.findBySynonym(alias);
            if (!aliasCandidates.isEmpty()) {
                candidates.addAll(aliasCandidates);
            }
        }
        if (candidates.isEmpty()) {
            for (String alias : aliases) {
                candidates.addAll(productNodeRepository.searchByKeyword(alias));
            }
        }

        candidates = candidates.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (candidates.isEmpty()) {
            return new IngredientMatchResult(ingredient, null, null, 0.0, "MISSING", "Không tìm thấy sản phẩm nào khớp trong Database.");
        }

        // 2. Score candidates based on name similarity and vector similarity
        List<ProductScore> scoredCandidates = new ArrayList<>();
        for (ProductNode p : candidates) {
            double score = calculateMatchScore(query, p, aliases);
            scoredCandidates.add(new ProductScore(p, score));
        }

        scoredCandidates.sort((a, b) -> Double.compare(b.score, a.score));

        if (scoredCandidates.isEmpty()) {
            return new IngredientMatchResult(ingredient, null, null, 0.0, "MISSING", "Không tìm thấy sản phẩm nào khớp trong Database.");
        }

        ProductScore best = scoredCandidates.get(0);

        // Check for ambiguity: if second best is too close to the best
        if (scoredCandidates.size() > 1) {
            ProductScore second = scoredCandidates.get(1);
            if (best.score > 0.6 && (best.score - second.score) < 0.15) {
                return new IngredientMatchResult(ingredient, best.product.getProductId(), best.product.getName(), best.score, "AMBIGUOUS", 
                    "Tìm thấy nhiều sản phẩm tương tự (vd: " + best.product.getName() + " vs " + second.product.getName() + "). Cần xác nhận lại.");
            }
        }

        if (best.score < 0.6) {
            return new IngredientMatchResult(ingredient, best.product.getProductId(), best.product.getName(), best.score, "AMBIGUOUS", 
                "Tìm thấy sản phẩm tương tự nhưng độ tin cậy thấp (" + best.product.getName() + ").");
        }

        return new IngredientMatchResult(ingredient, best.product.getProductId(), best.product.getName(), best.score, "MATCHED", 
            "Khớp chính xác thông qua phân tích đồ thị và ngữ nghĩa.");
    }

    private record ProductScore(ProductNode product, double score) {}

    private static final Set<String> DERIVED_TERMS = Set.of(
            "giam", "sot", "bot", "nuoc", "gia vi", "trich xuat", "huong", "say khô", "dong hop", "muoi"
    );

    private double calculateMatchScore(String query, ProductNode product, List<String> aliases) {
        String pName = normalizeText(product.getName());
        String q = normalizeText(query);
        Set<String> aliasSet = new LinkedHashSet<>(aliases);

        if (pName.equals(q) || aliasSet.contains(pName)) return 1.0;

        for (String alias : aliasSet) {
            if (pName.contains(alias) || alias.contains(pName)) {
                String[] aliasWords = alias.split("\\s+");
                if (aliasWords.length > 1) {
                    return 0.95;
                }
            }
        }

        if (q.equals("uc ga")) {
            boolean isBreast = pName.contains("uc ga") || (pName.contains("phi le") && pName.contains("ga"));
            boolean wrongPart = pName.contains("canh ga") || pName.contains("dui ga")
                    || pName.contains("chan ga") || pName.contains("long ga");
            if (wrongPart) {
                return 0.0;
            }
            if (isBreast) {
                return 0.95;
            }
        }

        String[] pWords = pName.split("\\s+");
        String[] qWords = q.split("\\s+");
        
        Set<String> pSet = new HashSet<>(Arrays.asList(pWords));
        Set<String> qSet = new HashSet<>(Arrays.asList(qWords));

        // 1. Base Score: Jaccard-like word overlap
        long intersect = qSet.stream().filter(pSet::contains).count();
        double score;
        if (pSet.containsAll(qSet)) {
            score = 0.85;
        } else {
            score = (double) intersect / Math.max(qSet.size(), pSet.size());
        }

        // 2. Penalty: Derived product mismatch (e.g., "táo" vs "giấm táo")
        for (String derived : DERIVED_TERMS) {
            if (pSet.contains(derived) && !qSet.contains(derived)) {
                score -= 0.4; // Heavy penalty if product is a derivative but query isn't
            }
        }

        // 3. Penalty: Modifier mismatch (e.g., "khoai lang" vs "khoai tây")
        // If query has a specific modifier that the product doesn't have, or vice versa
        if (qWords.length > 1 && pWords.length > 1) {
            boolean modifierMatch = false;
            for (int i = 1; i < qWords.length; i++) {
                if (pSet.contains(qWords[i])) {
                    modifierMatch = true;
                    score += 0.1; // Bonus for modifier match
                }
            }
            if (!modifierMatch && qWords.length > 1) {
                score -= 0.3;
            }
        }

        // 4. Bonus: Category context
        String cat = product.getCategoryName() != null ? normalizeText(product.getCategoryName()) : "";
        if (qSet.stream().anyMatch(cat::contains)) {
            score += 0.15;
        }

        // 5. Default bias for generic terms (e.g., "trứng" -> prefer "trứng gà" over "trứng cút")
        if (q.equals("trung") && pName.contains("ga")) score += 0.1;
        if (q.equals("sua") && pName.contains("tuoi")) score += 0.1;

        return Math.max(0.0, Math.min(1.0, score));
    }

    private List<String> expandAliases(String query) {
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(query);
        aliases.add(query.replace("sua tuoi tach beo", "sua tuoi"));
        aliases.add(query.replace("uc ga phi le", "uc ga"));
        aliases.add(query.replace("dau oliu", "olive oil"));
        aliases.add(query.replace("dau oliu", "dau o liu"));
        aliases.add(query.replace("oliu", "o liu"));
        aliases.add(query.replace("miy", "mi y"));
        aliases.add(query.replace("mi y", "spaghetti"));
        aliases.add(query.replace("trung ga", "trung"));
        aliases.add(query.replace("rau xanh", "xa lach"));
        aliases.add(query.replace("ca rot", "carrot"));
        aliases.add(query.replace("khoai tay", "potato"));
        return aliases.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return java.text.Normalizer.normalize(text.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Step 5: Thử nghiệm toàn diện.
     * Trả về báo cáo so sánh cho một danh sách các món ăn mẫu.
     */
    public Map<String, List<IngredientMatchResult>> runComprehensiveValidationTest(Map<String, List<String>> mealSamples) {
        Map<String, List<IngredientMatchResult>> report = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : mealSamples.entrySet()) {
            report.put(entry.getKey(), analyzeAndMatchIngredients(entry.getValue()));
        }
        return report;
    }
}
