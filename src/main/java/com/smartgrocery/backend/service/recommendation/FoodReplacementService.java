package com.smartgrocery.backend.service.recommendation;

import com.smartgrocery.backend.dto.RecommendationReplacementDto;
import com.smartgrocery.backend.dto.response.SubstitutionInspectionItemDto;
import com.smartgrocery.backend.dto.response.SubstitutionInspectionResponseDto;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FoodReplacementService {

    private final ProductVariantRepository productVariantRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final FoodFamilyCatalogService foodFamilyCatalogService;

    @Value("${app.ai.family-replacement-limit:3}")
    private int replacementLimit;

    public List<RecommendationReplacementDto> findNearestSubstitutes(Long sourceVariantId, String dietaryPreference, String allergies, int limit) {
        return scoreSubstitutes(sourceVariantId, dietaryPreference, allergies)
                .stream()
                .limit(Math.max(1, limit > 0 ? limit : replacementLimit))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Optional<RecommendationReplacementDto> findBestSubstitute(Long sourceVariantId, String dietaryPreference, String allergies) {
        return findNearestSubstitutes(sourceVariantId, dietaryPreference, allergies, 1).stream().findFirst();
    }

    public SubstitutionInspectionResponseDto inspectBySku(String sku, String dietaryPreference, String allergies, int limit) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU không được để trống");
        }

        ProductVariant source = productVariantRepository.findBySku(sku.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể với SKU: " + sku));
        List<ScoredReplacement> scored = scoreSubstitutes(source.getId(), dietaryPreference, allergies);
        int safeLimit = Math.max(1, limit > 0 ? limit : replacementLimit);

        List<SubstitutionInspectionItemDto> items = new ArrayList<>();
        int rank = 1;
        for (ScoredReplacement scoredReplacement : scored.stream().limit(safeLimit).toList()) {
            List<String> factors = buildFactorList(scoredReplacement);
            items.add(SubstitutionInspectionItemDto.builder()
                    .rank(rank++)
                    .variantId(scoredReplacement.profile().getVariantId())
                    .productId(scoredReplacement.profile().getProductId())
                    .sku(scoredReplacement.profile().getSku())
                    .name(scoredReplacement.profile().getProductName())
                    .family(scoredReplacement.profile().getPrimaryFamily().name())
                    .score(scoredReplacement.score())
                    .familySimilarity(scoredReplacement.familySimilarity())
                    .nutritionDistance(scoredReplacement.nutritionDistance())
                    .graphDistance(scoredReplacement.graphDistance())
                    .stock(scoredReplacement.stock())
                    .sharedTags(scoredReplacement.sharedTags())
                    .stockBonus(scoredReplacement.stockBonus())
                    .familyBonus(scoredReplacement.familyBonus())
                    .sharedTagBonus(scoredReplacement.sharedTagBonus())
                    .mismatchPenalty(scoredReplacement.mismatchPenalty())
                    .reason(FoodFamilyRules.buildReason(scoredReplacement.source(), scoredReplacement.profile(), scoredReplacement.graphDistance(), scoredReplacement.nutritionDistance(), scoredReplacement.sharedTags()))
                    .factors(factors)
                    .build());
        }

        return SubstitutionInspectionResponseDto.builder()
                .sourceSku(source.getSku())
                .sourceVariantId(source.getId())
                .sourceProductId(source.getProduct() != null ? source.getProduct().getId() : null)
                .sourceName(source.getProduct() != null ? source.getProduct().getName() : source.getVariantName())
                .limit(safeLimit)
                .dietaryPreference(dietaryPreference)
                .allergies(allergies)
                .generatedAt(java.time.LocalDateTime.now())
                .items(items)
                .build();
    }

    private List<ScoredReplacement> scoreSubstitutes(Long sourceVariantId, String dietaryPreference, String allergies) {
        if (sourceVariantId == null) {
            return List.of();
        }
        Map<Long, FoodFamilyProfile> catalog = foodFamilyCatalogService.buildCatalog();
        FoodFamilyProfile sourceProfile = catalog.get(sourceVariantId);
        if (sourceProfile == null) {
            return List.of();
        }

        Set<Long> candidateIds = new LinkedHashSet<>(catalog.keySet());
        candidateIds.remove(sourceVariantId);

        Map<Long, Integer> stockByVariantId = inventoryStockRepository.sumAvailableByVariantIds(candidateIds.stream().toList()).stream()
                .collect(Collectors.toMap(
                        InventoryStockRepository.VariantStockSum::getVariantId,
                        stock -> stock.getTotalAvailable() == null ? 0 : stock.getTotalAvailable().intValue(),
                        (a, b) -> a + b,
                        LinkedHashMap::new));

        List<ScoredReplacement> scored = new ArrayList<>();
        for (Long candidateId : candidateIds) {
            FoodFamilyProfile candidateProfile = catalog.get(candidateId);
            if (candidateProfile == null) continue;

            int stock = candidateProfile.getAvailableStock() != null ? candidateProfile.getAvailableStock() : stockByVariantId.getOrDefault(candidateId, 0);
            if (stock <= 0) continue;
            if (!FoodFamilyRules.satisfiesCalorieBand(sourceProfile, candidateProfile)) continue;
            if (FoodFamilyRules.violatesAllergy(candidateProfile, allergies) || FoodFamilyRules.violatesDietPreference(candidateProfile, dietaryPreference)) continue;

            double familySimilarity = FoodFamilyRules.familySimilarity(sourceProfile.getFamilyTags(), candidateProfile.getFamilyTags());
            double nutritionDistance = FoodFamilyRules.nutritionDistance(sourceProfile, candidateProfile);
            int sharedTags = sharedTagCount(sourceProfile.getFamilyTags(), candidateProfile.getFamilyTags());
            double stockBonus = stock > 0 ? 0.12 : 0.0;
            double familyBonus = candidateProfile.matchesFamily(sourceProfile.getPrimaryFamily()) ? 0.10 : 0.0;
            double sharedTagBonus = sharedTags > 0 ? 0.08 : 0.0;
            double mismatchPenalty = penaltyForMismatch(sourceProfile, candidateProfile);
            double graphDistance = 2.0 - familySimilarity;
            double score = (familySimilarity * 0.46)
                    + ((1.0 - nutritionDistance) * 0.34)
                    + stockBonus
                    + sharedTagBonus
                    + familyBonus
                    - mismatchPenalty;

            scored.add(new ScoredReplacement(candidateProfile, sourceProfile, graphDistance, familySimilarity, nutritionDistance, sharedTags, stock, stockBonus, familyBonus, sharedTagBonus, mismatchPenalty, score));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredReplacement::score).reversed()
                        .thenComparingDouble(ScoredReplacement::nutritionDistance)
                        .thenComparing(Comparator.comparingInt(ScoredReplacement::stock).reversed()))
                .toList();
    }

    private RecommendationReplacementDto toDto(ScoredReplacement scored) {
        FoodFamilyProfile profile = scored.profile();
        String reason = FoodFamilyRules.buildReason(scored.source(), profile, scored.graphDistance(), scored.nutritionDistance(), scored.sharedTags());
        return RecommendationReplacementDto.builder()
                .variantId(profile.getVariantId())
                .productId(profile.getProductId())
                .sku(profile.getSku())
                .name(profile.getProductName())
                .price(null)
                .stock(scored.stock())
                .family(profile.getPrimaryFamily().name())
                .matchedFamilies(profile.getFamilyTags().stream().map(Enum::name).toList())
                .graphDistance(scored.graphDistance())
                .nutritionDistance(scored.nutritionDistance())
                .reason(reason)
                .recommended(Boolean.TRUE)
                .build();
    }

    private List<String> buildFactorList(ScoredReplacement scored) {
        List<String> factors = new ArrayList<>();
        factors.add("familySimilarity=" + round(scored.familySimilarity()));
        factors.add("nutritionDistance=" + round(scored.nutritionDistance()));
        factors.add("graphDistance=" + round(scored.graphDistance()));
        factors.add("stockBonus=" + round(scored.stockBonus()));
        factors.add("familyBonus=" + round(scored.familyBonus()));
        factors.add("sharedTagBonus=" + round(scored.sharedTagBonus()));
        factors.add("mismatchPenalty=" + round(scored.mismatchPenalty()));
        factors.add("stock=" + scored.stock());
        factors.add("sharedTags=" + scored.sharedTags());
        return factors;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private int sharedTagCount(Set<FoodFamilyGroup> left, Set<FoodFamilyGroup> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<FoodFamilyGroup> shared = new LinkedHashSet<>(left);
        shared.retainAll(right);
        return shared.size();
    }

    private double penaltyForMismatch(FoodFamilyProfile source, FoodFamilyProfile candidate) {
        double penalty = 0.0;
        if (source == null || candidate == null) {
            return penalty;
        }
        if (!Objects.equals(source.getPrimaryFamily(), candidate.getPrimaryFamily())) {
            penalty += 0.12;
        }
        if (source.matchesFamily(FoodFamilyGroup.PROTEIN) && candidate.matchesFamily(FoodFamilyGroup.VEGETABLE)) {
            penalty += 0.25;
        }
        if (source.matchesFamily(FoodFamilyGroup.DESSERT) && candidate.matchesFamily(FoodFamilyGroup.PROTEIN)) {
            penalty += 0.20;
        }
        if (source.matchesFamily(FoodFamilyGroup.PROTEIN) && candidate.matchesFamily(FoodFamilyGroup.CARBOHYDRATE) && !candidate.matchesFamily(FoodFamilyGroup.PROTEIN)) {
            penalty += 0.08;
        }
        return penalty;
    }

        private record ScoredReplacement(
            FoodFamilyProfile profile,
            FoodFamilyProfile source,
            double graphDistance,
            double familySimilarity,
            double nutritionDistance,
            int sharedTags,
            int stock,
            double stockBonus,
            double familyBonus,
            double sharedTagBonus,
            double mismatchPenalty,
            double score) {
    }
}
