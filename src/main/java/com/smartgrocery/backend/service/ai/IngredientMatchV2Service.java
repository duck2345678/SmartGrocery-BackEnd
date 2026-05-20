package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.IngredientCanonical;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import com.smartgrocery.backend.repository.jpa.IngredientAliasRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientMatchV2Service {

    private final IngredientTextNormalizer normalizer;
    private final IngredientAliasRepository ingredientAliasRepository;
    private final ProductRepository productRepository;
    private final ProductNodeRepository productNodeRepository;

    public MatchDecision match(String ingredientText, Collection<Long> stockedProductIds) {
        String normalized = normalizer.normalize(ingredientText);
        if (normalized.isBlank()) {
            return MatchDecision.missing(ingredientText, "EMPTY_INGREDIENT");
        }

        Optional<ProductNodeRepository.CanonicalAliasProjection> neoAliasOpt =
                productNodeRepository.findCanonicalByAliasNorm(normalized, "vi");

        IngredientCanonical canonical;
        if (neoAliasOpt.isPresent()) {
            ProductNodeRepository.CanonicalAliasProjection p = neoAliasOpt.get();
            canonical = IngredientCanonical.builder()
                    .id(p.getCanonicalId())
                    .canonicalCode(p.getCanonicalCode())
                    .canonicalNameVi(p.getCanonicalNameVi())
                    .ingredientFamily(p.getIngredientFamily())
                    .defaultDimension(p.getDefaultDimension())
                    .averageWeightPerUnitG(p.getAvgWeightG() == null ? null : java.math.BigDecimal.valueOf(p.getAvgWeightG()))
                    .averageVolumePerUnitMl(p.getAvgVolumeMl() == null ? null : java.math.BigDecimal.valueOf(p.getAvgVolumeMl()))
                    .active(true)
                    .build();
        } else {
            canonical = ingredientAliasRepository.findFirstByAliasTextNormAndLangAndActiveTrue(normalized, "vi")
                    .map(a -> a.getCanonical())
                    .orElse(null);
        }

        if (canonical == null) {
            return MatchDecision.ambiguous(ingredientText, "CANONICAL_ALIAS_NOT_FOUND");
        }
        if (canonical == null || !Boolean.TRUE.equals(canonical.getActive())) {
            return MatchDecision.ambiguous(ingredientText, "CANONICAL_INACTIVE");
        }

        Set<Long> graphCandidateIds = new HashSet<>();
        if (canonical.getId() != null) {
            List<ProductNode> graphCandidates = productNodeRepository.findProductsByCanonicalId(canonical.getId());
            graphCandidates.stream()
                    .map(ProductNode::getProductId)
                    .filter(Objects::nonNull)
                    .forEach(graphCandidateIds::add);
        }

        List<Product> activeProducts = graphCandidateIds.isEmpty()
                ? productRepository.findActiveWithCategory()
                : productRepository.findAllByIdWithCategory(new ArrayList<>(graphCandidateIds));
        List<Candidate> candidates = new ArrayList<>();
        for (Product product : activeProducts) {
            if (product == null || product.getId() == null) {
                continue;
            }
            if (stockedProductIds != null && !stockedProductIds.isEmpty() && !stockedProductIds.contains(product.getId())) {
                continue;
            }
            int score = scoreProduct(canonical, normalized, product);
            if (score > 0) {
                candidates.add(new Candidate(product, score));
            }
        }
        candidates.sort((a, b) -> Integer.compare(b.score, a.score));
        if (candidates.isEmpty()) {
            return MatchDecision.missing(ingredientText, "NO_PRODUCT_MATCH");
        }

        Candidate best = candidates.get(0);
        if (candidates.size() > 1) {
            Candidate second = candidates.get(1);
            if (best.score - second.score < 15) {
                return MatchDecision.ambiguous(ingredientText,
                        "AMBIGUOUS_MATCH top1=" + best.product.getName() + ", top2=" + second.product.getName());
            }
        }
        return MatchDecision.matched(ingredientText, canonical, best.product, "MATCHED_SCORE_" + best.score);
    }

    private int scoreProduct(IngredientCanonical canonical, String normalizedIngredient, Product product) {
        String name = normalizer.normalize(product.getName());
        String category = safeCategory(product);
        int score = 0;

        if (name.equals(normalizedIngredient)) {
            score += 120;
        }
        if (name.contains(normalizedIngredient)) {
            score += 70;
        }
        for (String token : normalizedIngredient.split("\\s+")) {
            if (token.length() < 2) continue;
            if (name.contains(token)) {
                score += 14;
            }
        }
        if (category.contains(normalizer.normalize(canonical.getIngredientFamily()))) {
            score += 20;
        }

        // processed-vs-fresh guard
        boolean ingredientFresh = normalizedIngredient.contains("tuoi");
        boolean productProcessed = name.contains("bot") || name.contains("dong hop") || name.contains("tuong")
                || name.contains("sot") || name.contains("nuoc");
        if (ingredientFresh && productProcessed) {
            score -= 45;
        }
        if (normalizedIngredient.contains("ot hiem") && name.contains("cherry")) {
            score -= 80;
        }
        return score;
    }

    private String safeCategory(Product product) {
        try {
            return normalizer.normalize(product.getCategory() != null ? product.getCategory().getName() : "");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private record Candidate(Product product, int score) {}

    public record MatchDecision(
            String originalIngredient,
            String status,
            IngredientCanonical canonical,
            Product matchedProduct,
            String reasonCode
    ) {
        public static MatchDecision matched(String original, IngredientCanonical canonical, Product matchedProduct, String reason) {
            return new MatchDecision(original, "MATCHED", canonical, matchedProduct, reason);
        }

        public static MatchDecision ambiguous(String original, String reason) {
            return new MatchDecision(original, "AMBIGUOUS", null, null, reason);
        }

        public static MatchDecision missing(String original, String reason) {
            return new MatchDecision(original, "MISSING", null, null, reason);
        }
    }
}
