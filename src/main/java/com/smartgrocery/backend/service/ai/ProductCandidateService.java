package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCandidateService {

    private final ProductRepository productRepository;
    private final NeedExtractionService needExtractionService;
    private final ShoppingActionValidator shoppingActionValidator;

    public List<Product> findExplicitlyRequestedProducts(String userMessage) {
        List<String> requestedPhrases = extractDirectProductPhrases(userMessage);
        if (requestedPhrases.isEmpty()) {
            return List.of();
        }

        List<Product> activeProducts;
        try {
            activeProducts = productRepository.findActiveWithCategory();
        } catch (Exception e) {
            log.warn("Could not load active products for direct product shopping: {}", e.getMessage());
            return List.of();
        }

        LinkedHashMap<Long, Product> matchedProducts = new LinkedHashMap<>();
        for (String phrase : requestedPhrases) {
            List<String> aliases = directProductAliases(phrase);
            activeProducts.stream()
                    .filter(product -> product.getId() != null)
                    .filter(product -> aliases.stream().anyMatch(alias -> directProductMatchesPhrase(product, alias)))
                    .max(Comparator.comparingInt(product -> aliases.stream()
                            .mapToInt(alias -> directProductMatchScore(product, alias))
                            .max()
                            .orElse(0)))
                    .ifPresent(product -> matchedProducts.putIfAbsent(product.getId(), product));
        }
        return matchedProducts.values().stream()
                .limit(12)
                .toList();
    }

    public List<Product> findCandidatesForNeeds(NeedExtractionService.NeedAnalysis analysis) {
        if (analysis == null || analysis.needs().isEmpty()) {
            return List.of();
        }
        List<Product> activeProducts;
        try {
            activeProducts = productRepository.findActiveWithCategory();
        } catch (Exception e) {
            log.warn("Could not load active products for need candidate search: {}", e.getMessage());
            return List.of();
        }

        List<String> terms = candidateTermsForNeeds(analysis);
        if (terms.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<Long, Product> candidates = new LinkedHashMap<>();
        activeProducts.stream()
                .filter(product -> product.getId() != null)
                .filter(product -> terms.stream().anyMatch(term ->
                        needExtractionService.containsNormalizedPhrase(productSearchText(product), term)))
                .sorted(Comparator.comparingInt((Product product) -> needCandidateScore(product, terms, analysis)).reversed())
                .forEach(product -> candidates.putIfAbsent(product.getId(), product));

        Set<Long> stockedIds = shoppingActionValidator.findActiveStockedProductIds(candidates.keySet());
        return candidates.values().stream()
                .filter(product -> stockedIds.contains(product.getId()))
                .limit(12)
                .toList();
    }

    public List<String> candidateTermsForNeeds(NeedExtractionService.NeedAnalysis analysis) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.addAll(analysis.directProductTerms());
        if (analysis.hasNeed(NeedExtractionService.Need.DISHWASHING)) {
            terms.addAll(List.of("nuoc rua chen", "nuoc rua bat"));
        }
        if (analysis.hasNeed(NeedExtractionService.Need.LAUNDRY)) {
            terms.addAll(List.of("nuoc giat", "bot giat"));
        }
        if (analysis.hasNeed(NeedExtractionService.Need.HOUSEHOLD_CLEANING)) {
            terms.addAll(List.of("nuoc lau san", "nuoc rua chen", "khan giay", "giay ve sinh"));
        }
        if (analysis.hasNeed(NeedExtractionService.Need.DRINK)) {
            terms.addAll(List.of("nuoc khoang", "tra xanh", "nuoc ep", "soda"));
        }
        if (analysis.hasNeed(NeedExtractionService.Need.SNACK_SWEET)) {
            terms.addAll(List.of("banh", "socola", "keo", "sua chua"));
        }
        if (analysis.hasNeed(NeedExtractionService.Need.FOOD_MEAL)) {
            terms.addAll(List.of("trung", "uc ga", "khoai lang", "yen mach", "rau"));
        }
        return terms.stream().toList();
    }

    public List<String> extractDirectProductPhrases(String userMessage) {
        String n = needExtractionService.normalizeText((userMessage == null ? "" : userMessage)
                .replace(",", " va ")
                .replace(";", " va ")
                .replace("/", " va "));
        if (n.isBlank()) {
            return List.of();
        }

        List<String> explicitTerms = needExtractionService.extractDirectProductTerms(n);
        if (!explicitTerms.isEmpty()) {
            return explicitTerms;
        }

        String requestText = n;
        for (String prefix : List.of(
                "tao danh sach mua sam cho",
                "tao danh sach mua hang cho",
                "tao danh sach mua do cho",
                "lap danh sach mua sam cho",
                "lap danh sach mua hang cho",
                "danh sach mua sam cho",
                "danh sach mua hang cho",
                "tao list mua do cho",
                "list mua do cho",
                "mua sam cho",
                "mua do cho",
                "mua cho",
                "them vao gio",
                "bo vao gio",
                "cho vao gio")) {
            if (requestText.contains(prefix)) {
                requestText = requestText.substring(requestText.indexOf(prefix) + prefix.length()).trim();
                break;
            }
        }

        for (String generic : List.of(
                "tao", "lap", "danh sach", "mua sam", "mua hang", "mua do",
                "shopping list", "list mua", "cho", "toi", "minh", "dum",
                "giup", "ho", "nhe", "nha", "can", "cac san pham", "san pham")) {
            requestText = requestText.replace(generic, " ");
        }
        requestText = requestText.replaceAll("\\s+", " ").trim();
        if (requestText.isBlank()) {
            return List.of();
        }

        return Arrays.stream(requestText.split("\\b(?:va|voi|cung|gom|them)\\b"))
                .map(String::trim)
                .map(phrase -> phrase.replaceAll("\\s+", " "))
                .filter(phrase -> phrase.length() >= 3)
                .distinct()
                .toList();
    }

    public boolean directProductMatchesPhrase(Product product, String phrase) {
        String normalizedPhrase = needExtractionService.normalizeText(phrase);
        if (normalizedPhrase.isBlank()) {
            return false;
        }
        String haystack = directProductSearchText(product);
        if (needExtractionService.containsNormalizedPhrase(haystack, normalizedPhrase)) {
            return true;
        }

        String name = needExtractionService.normalizeText(product != null ? product.getName() : "");
        List<String> nameTokens = meaningfulProductTokens(name);
        for (int i = 0; i + 1 < nameTokens.size(); i++) {
            String productPhrase = nameTokens.get(i) + " " + nameTokens.get(i + 1);
            if (needExtractionService.containsNormalizedPhrase(normalizedPhrase, productPhrase)) {
                return true;
            }
        }
        return false;
    }

    private List<String> directProductAliases(String phrase) {
        String n = needExtractionService.normalizeText(phrase);
        if (needExtractionService.containsNormalizedPhrase(n, "dau an")) {
            return List.of("dau an", "dau an huong duong");
        }
        if (needExtractionService.containsNormalizedPhrase(n, "hat nem")) {
            return List.of("hat nem", "hat nem tu thit");
        }
        if (needExtractionService.containsNormalizedPhrase(n, "ca phe")) {
            return List.of("ca phe", "ca phe hoa tan");
        }
        if (needExtractionService.containsNormalizedPhrase(n, "sua hanh nhan")) {
            return List.of("sua hanh nhan", "sua hanh nhan khong duong");
        }
        if (needExtractionService.containsNormalizedPhrase(n, "nuoc giat")) {
            return List.of("nuoc giat", "nuoc giat ariel");
        }
        if (needExtractionService.containsNormalizedPhrase(n, "nuoc rua chen")) {
            return List.of("nuoc rua chen", "nuoc rua bat");
        }
        return List.of(n);
    }

    private int directProductMatchScore(Product product, String phrase) {
        String normalizedPhrase = needExtractionService.normalizeText(phrase);
        String name = needExtractionService.normalizeText(product != null ? product.getName() : "");
        String haystack = directProductSearchText(product);
        int score = 0;
        if (name.equals(normalizedPhrase)) {
            score += 300;
        }
        if (needExtractionService.containsNormalizedPhrase(name, normalizedPhrase)) {
            score += 180 + normalizedPhrase.length();
        }
        if (needExtractionService.containsNormalizedPhrase(haystack, normalizedPhrase)) {
            score += 120 + normalizedPhrase.length();
        }
        List<String> phraseTokens = meaningfulProductTokens(normalizedPhrase);
        for (String token : phraseTokens) {
            if (needExtractionService.containsNormalizedPhrase(name, token)) {
                score += 20;
            } else if (needExtractionService.containsNormalizedPhrase(haystack, token)) {
                score += 8;
            }
        }
        return score;
    }

    private int needCandidateScore(Product product, List<String> terms, NeedExtractionService.NeedAnalysis analysis) {
        String haystack = productSearchText(product);
        int score = 0;
        for (String term : terms) {
            if (needExtractionService.containsNormalizedPhrase(haystack, term)) {
                score += 20 + term.length();
            }
        }
        if (analysis.hasConstraint(NeedExtractionService.Constraint.LOW_BUDGET)
                && Boolean.TRUE.equals(product.getIsStaple())) {
            score += 6;
        }
        return score;
    }

    private List<String> meaningfulProductTokens(String text) {
        return Arrays.stream(needExtractionService.normalizeText(text).split("\\s+"))
                .filter(token -> token.length() >= 2)
                .filter(token -> !Set.of(
                        "va", "voi", "cho", "mua", "san", "pham", "loai",
                        "hop", "chai", "goi", "kg", "ml", "lit").contains(token))
                .toList();
    }

    private String directProductSearchText(Product product) {
        if (product == null) {
            return "";
        }
        return needExtractionService.normalizeText(String.join(" ",
                product.getName() != null ? product.getName() : "",
                safeCategoryName(product)
        ));
    }

    private String productSearchText(Product product) {
        if (product == null) {
            return "";
        }
        return needExtractionService.normalizeText(String.join(" ",
                product.getName() != null ? product.getName() : "",
                safeCategoryName(product),
                product.getShortDescription() != null ? product.getShortDescription() : "",
                product.getDescription() != null ? product.getDescription() : ""
        ));
    }

    private String safeCategoryName(Product product) {
        if (product == null || product.getCategory() == null) {
            return "";
        }
        try {
            return product.getCategory().getName();
        } catch (RuntimeException e) {
            return "";
        }
    }
}
