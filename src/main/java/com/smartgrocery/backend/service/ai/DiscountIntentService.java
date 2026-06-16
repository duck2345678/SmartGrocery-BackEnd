package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.dto.ChatResponseDto;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.WishlistItem;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountIntentService {

    private final ProductVariantRepository productVariantRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;
    private final ShoppingItemBuilder shoppingItemBuilder;

    private static final Pattern DISCOUNT_QUERY_PATTERN = Pattern.compile("(?iu).*(giam\\s*gia|sale|khuyen\\s*mai).*");
    private static final Pattern WISHLIST_QUERY_PATTERN = Pattern.compile("(?iu).*(wishlist|yeu\\s*thich|da\\s*luu|danh\\s*sach\\s*yeu\\s*thich).*");

    private static final Set<String> DISCOUNT_KEYWORD_NOISE_WORDS = Set.of(
            "co", "khong", "ko", "k", "hok", "dang", "duoc", "hom", "nay",
            "nao", "gi", "nhi", "ha", "a", "vay", "khuyen", "mai", "giam", "gia", "sale"
    );

    public record DiscountIntentResult(String reply, List<ChatResponseDto.ShoppingItem> shoppingItems) {
        public static DiscountIntentResult none() {
            return new DiscountIntentResult(null, null);
        }

        public static DiscountIntentResult of(String reply, List<ChatResponseDto.ShoppingItem> shoppingItems) {
            return new DiscountIntentResult(reply, shoppingItems);
        }
    }

    public record DiscountIntentExtraction(String intent, String productName) {}

    public DiscountIntentResult detectDiscountIntent(String userMessage, Long userId) {
        if (userMessage == null || userMessage.isBlank()) {
            return DiscountIntentResult.none();
        }

        String trimmed = userMessage.trim();
        String normalized = normalizeVietnamese(trimmed);
        if (!DISCOUNT_QUERY_PATTERN.matcher(normalized).matches()) {
            return DiscountIntentResult.none();
        }

        log.info("[DiscountIntent] Detected discount query: raw='{}' normalized='{}'", trimmed, normalized);

        if (WISHLIST_QUERY_PATTERN.matcher(normalized).matches()) {
            List<ProductVariant> wishlistDiscounts = findDiscountedWishlistVariants(userId);
            if (wishlistDiscounts.isEmpty()) {
                return DiscountIntentResult.of(
                        "Hiện tại mình chưa thấy sản phẩm nào trong wishlist của bạn đang giảm giá.",
                        null
                );
            }
            return DiscountIntentResult.of(
                    String.format("Mình tìm thấy %d sản phẩm trong wishlist của bạn đang giảm giá hôm nay.", wishlistDiscounts.size()),
                    shoppingItemBuilder.buildShoppingItemsFromVariants(wishlistDiscounts)
            );
        }

        String rawKeyword = extractDiscountKeyword(trimmed, false);
        String normalizedKeyword = extractDiscountKeyword(normalized, true);
        String accentedLabel = extractAccentedProductLabel(trimmed);
        Optional<DiscountIntentExtraction> aiExtraction = extractDiscountIntentWithAi(trimmed);
        if (aiExtraction.isPresent() && hasText(aiExtraction.get().productName())) {
            rawKeyword = aiExtraction.get().productName().trim();
            normalizedKeyword = normalizeVietnamese(rawKeyword);
            accentedLabel = rawKeyword;
        }
        boolean specificDiscountQuestion = looksLikeSpecificDiscountQuestion(normalized, normalizedKeyword);
        log.info("[DiscountIntent] Extracted keywords: rawKeyword='{}' normalizedKeyword='{}' accentedLabel='{}' specificQ={}", rawKeyword, normalizedKeyword, accentedLabel, specificDiscountQuestion);

        if (hasText(rawKeyword) || hasText(normalizedKeyword)) {
            List<ProductVariant> discountedMatches = findDiscountedVariantsByKeyword(rawKeyword, normalizedKeyword);
            log.info("[DiscountIntent] Discounted matches found: {}", discountedMatches.size());
            if (!discountedMatches.isEmpty()) {
                String productName = discountedMatches.get(0).getProduct().getName();
                return DiscountIntentResult.of(
                        String.format("%s đang có giảm giá hôm nay. Mình đã đưa các phiên bản đang giảm giá ở bên dưới nhé.", productName),
                        shoppingItemBuilder.buildShoppingItemsFromVariants(discountedMatches)
                );
            }

            List<ProductVariant> activeMatches = findActiveVariantsByKeyword(rawKeyword, normalizedKeyword);
            log.info("[DiscountIntent] Active (non-discounted) matches found: {}", activeMatches.size());
            if (!activeMatches.isEmpty()) {
                return DiscountIntentResult.of(
                        String.format("Mình chưa thấy %s có giảm giá ở thời điểm hiện tại.", activeMatches.get(0).getProduct().getName()),
                        null
                );
            }
        }

        if (specificDiscountQuestion) {
            String displayLabel = hasText(accentedLabel) ? accentedLabel : (hasText(rawKeyword) ? rawKeyword : normalizedKeyword);
            return DiscountIntentResult.of(
                    String.format("Mình chưa tìm thấy sản phẩm phù hợp với \"%s\" trong danh mục giảm giá hiện tại. Bạn thử ghi tên cụ thể hơn giúp mình nhé.", displayLabel),
                    null
            );
        }

        List<ProductVariant> expandedDiscounts = productVariantRepository.findAllDiscountedVariants()
                .stream()
                .limit(20)
                .collect(Collectors.toList());
        if (expandedDiscounts.isEmpty()) {
            return DiscountIntentResult.of("Hiện tại chưa có sản phẩm nào đang giảm giá.", null);
        }
        return DiscountIntentResult.of(
                String.format("Hôm nay mình tìm thấy %d sản phẩm đang giảm giá. Mình đã gợi ý danh sách bên dưới để bạn xem nhanh nhé.", expandedDiscounts.size()),
                shoppingItemBuilder.buildShoppingItemsFromVariants(expandedDiscounts)
        );
    }

    public List<ProductVariant> findDiscountedWishlistVariants(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<WishlistItem> wishlistItems = wishlistItemRepository.findByWishlist_UserId(userId);
        if (wishlistItems.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = wishlistItems.stream()
                .map(WishlistItem::getProduct)
                .filter(Objects::nonNull)
                .map(Product::getId)
                .distinct()
                .collect(Collectors.toList());
        if (productIds.isEmpty()) {
            return List.of();
        }
        return deduplicateVariantsByProduct(productVariantRepository.findDiscountedVariantsByProductIds(productIds));
    }

    public List<ProductVariant> findDiscountedVariantsByKeyword(String rawKeyword, String normalizedKeyword) {
        String queryKeyword = hasText(rawKeyword) ? rawKeyword : normalizedKeyword;
        if (!hasText(queryKeyword)) {
            return List.of();
        }
        List<ProductVariant> matches = productVariantRepository.findDiscountedVariantsByKeyword(queryKeyword);
        List<ProductVariant> exactPhraseMatches = matches.stream()
                .filter(variant -> productNameMatchesKeyword(variant, rawKeyword, normalizedKeyword))
                .collect(Collectors.toList());
        if (!exactPhraseMatches.isEmpty()) {
            return deduplicateVariantsByProduct(exactPhraseMatches);
        }

        if (hasText(normalizedKeyword) && !normalizedKeyword.equalsIgnoreCase(queryKeyword)) {
            List<ProductVariant> normalizedMatches = productVariantRepository.findDiscountedVariantsByKeyword(normalizedKeyword)
                    .stream()
                    .filter(variant -> productNameMatchesKeyword(variant, rawKeyword, normalizedKeyword))
                    .collect(Collectors.toList());
            return deduplicateVariantsByProduct(normalizedMatches);
        }
        return List.of();
    }

    public List<ProductVariant> findActiveVariantsByKeyword(String rawKeyword, String normalizedKeyword) {
        if (hasText(rawKeyword)) {
            List<ProductVariant> rawMatches = productVariantRepository.findTop10ActiveByKeyword(rawKeyword);
            if (!rawMatches.isEmpty()) {
                List<ProductVariant> exactRawMatches = rawMatches.stream()
                        .filter(variant -> productNameMatchesKeyword(variant, rawKeyword, normalizedKeyword))
                        .collect(Collectors.toList());
                if (!exactRawMatches.isEmpty()) {
                    return exactRawMatches;
                }
            }
        }
        if (!hasText(normalizedKeyword)) {
            return List.of();
        }
        List<ProductVariant> normalizedMatches = productVariantRepository.findActiveVariantsByKeywordNameOnly(normalizedKeyword);
        if (!normalizedMatches.isEmpty()) {
            return normalizedMatches.stream()
                    .filter(variant -> productNameMatchesKeyword(variant, rawKeyword, normalizedKeyword))
                    .limit(10)
                    .collect(Collectors.toList());
        }
        return productVariantRepository.searchActiveForSubstitution(normalizedKeyword).stream()
                .filter(variant -> variant.getProduct() != null)
                .filter(variant -> productNameMatchesKeyword(variant, rawKeyword, normalizedKeyword))
                .limit(10)
                .collect(Collectors.toList());
    }

    public boolean productNameMatchesKeyword(ProductVariant variant, String rawKeyword, String normalizedKeyword) {
        if (variant == null || variant.getProduct() == null || variant.getProduct().getName() == null) {
            return false;
        }
        String productName = variant.getProduct().getName();
        if (!hasText(normalizedKeyword)) {
            return false;
        }
        if (isAmbiguousAccentlessKeyword(normalizedKeyword)
                && !matchesAmbiguousKeywordContext(productName, normalizedKeyword)) {
            return false;
        }
        if (hasText(rawKeyword) && productName.toLowerCase(Locale.ROOT).contains(rawKeyword.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return containsNormalizedWords(productName, normalizedKeyword);
    }

    public boolean isAmbiguousAccentlessKeyword(String normalizedKeyword) {
        if (!hasText(normalizedKeyword)) {
            return false;
        }
        boolean singleWord = !normalizedKeyword.trim().contains(" ");
        return singleWord && Set.of("trung").contains(normalizedKeyword.trim());
    }

    public boolean matchesAmbiguousKeywordContext(String productName, String normalizedKeyword) {
        String normalizedProductName = " " + normalizeVietnamese(productName).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        if ("trung".equals(normalizedKeyword.trim())) {
            if (normalizedProductName.contains(" noi dia trung ")
                    || normalizedProductName.contains(" trung nguyen ")
                    || normalizedProductName.contains(" trung quoc ")) {
                return false;
            }
            return normalizedProductName.contains(" trung ga ")
                    || normalizedProductName.contains(" trung vit ")
                    || normalizedProductName.contains(" trung cut ")
                    || normalizedProductName.contains(" trung tuoi ")
                    || normalizedProductName.contains(" trung muoi ")
                    || normalizedProductName.contains(" trung ran ")
                    || normalizedProductName.contains(" trung luoc ")
                    || normalizedProductName.contains(" trung chien ")
                    || normalizedProductName.contains(" hop trung ")
                    || normalizedProductName.contains(" vi trung ")
                    || normalizedProductName.contains(" khay trung ")
                    || normalizedProductName.contains(" lo trung ")
                    || normalizedProductName.contains(" qua trung ")
                    || normalizedProductName.startsWith(" trung ");
        }
        return false;
    }

    public boolean containsNormalizedWords(String text, String normalizedKeyword) {
        String normalizedText = " " + normalizeVietnamese(text).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        String keyword = normalizeVietnamese(normalizedKeyword).replaceAll("[^a-z0-9]+", " ").trim();
        if (keyword.isBlank()) {
            return false;
        }
        return normalizedText.contains(" " + keyword + " ");
    }

    public String extractDiscountKeyword(String message, boolean normalizedInput) {
        if (message == null) {
            return null;
        }
        String tokenBasedKeyword = extractDiscountKeywordByTokens(message);
        if (hasText(tokenBasedKeyword)) {
            return tokenBasedKeyword;
        }

        String keyword = message
                .replaceAll(normalizedInput
                                ? "\\b(san pham|mat hang|wishlist|yeu thich|da luu|danh sach yeu thich)\\b"
                                : "(?iu)\\b(sản phẩm|san pham|mặt hàng|mat hang|wishlist|yêu thích|yeu thich|đã lưu|da luu|danh sách yêu thích|danh sach yeu thich)\\b",
                        " ")
                .replaceAll(normalizedInput
                                ? "\\b(co|khong|khong co|dang|hom nay|nao|gi|khuyen mai|giam gia|sale)\\b"
                                : "(?iu)\\b(có|co|không|khong|không có|khong co|đang|dang|hôm nay|hom nay|nào|nao|gì|gi|khuyến mại|khuyen mai|khuyến mãi|khuyen mai|giảm giá|giam gia|sale)\\b",
                        " ")
                .replaceAll("\\s+", " ")
                .trim();
        keyword = removeDiscountKeywordNoiseWords(keyword);
        return keyword.length() >= 2 ? keyword : null;
    }

    public String extractDiscountKeywordByTokens(String message) {
        if (!hasText(message)) {
            return null;
        }
        String normalized = normalizeVietnamese(message)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (!hasText(normalized)) {
            return null;
        }
        String keyword = Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !DISCOUNT_KEYWORD_NOISE_WORDS.contains(token))
                .collect(Collectors.joining(" "))
                .trim();
        return keyword.length() >= 2 ? keyword : null;
    }

    public String extractAccentedProductLabel(String originalMessage) {
        if (!hasText(originalMessage)) {
            return null;
        }
        String label = originalMessage
                .replaceAll("(?iu)\\b(có|không|ko|k|hok|đang|được|hôm nay|nào|gì|nhỉ|hả|à|vậy|khuyến mại|khuyến mãi|giảm giá|sale|sản phẩm|mặt hàng)\\b", " ")
                .replaceAll("[?!.,]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return label.length() >= 2 ? label : null;
    }

    public Optional<DiscountIntentExtraction> extractDiscountIntentWithAi(String userMessage) {
        if (!hasText(userMessage) || openRouterClient == null || objectMapper == null) {
            return Optional.empty();
        }
        String systemPrompt = """
                You extract shopping intent from Vietnamese user messages.
                Return only JSON with this shape:
                {"intent":"check_discount"|"show_discounts"|"other","product_name":string|null}
                Rules:
                - Use check_discount only when the user asks whether a specific product is discounted/on sale/promoted.
                - Use show_discounts for general sale-list questions without a specific product.
                - product_name must be the exact product phrase, preserving Vietnamese accents when present.
                - Do not invent product names. If unsure, use null.
                """;
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", userMessage));
        try {
            OpenRouterClient.AiCompletionResult result = openRouterClient
                    .chatCompletion(systemPrompt, messages, null, Duration.ofSeconds(5))
                    .block();
            if (result == null || !result.isSuccess() || !hasText(result.getReply())) {
                return Optional.empty();
            }
            return parseDiscountIntentExtraction(result.getReply());
        } catch (Exception e) {
            log.debug("[DiscountIntent] AI extraction fallback to local parser: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<DiscountIntentExtraction> parseDiscountIntentExtraction(String reply) {
        try {
            JsonNode root = objectMapper.readTree(reply);
            String intent = root.path("intent").asText("");
            JsonNode productNode = root.get("product_name");
            String productName = productNode == null || productNode.isNull() ? null : productNode.asText(null);
            if (!"check_discount".equals(intent) || productName == null || productName.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new DiscountIntentExtraction(intent, productName.trim()));
        } catch (Exception e) {
            log.debug("[DiscountIntent] Could not parse AI extraction JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String removeDiscountKeywordNoiseWords(String keyword) {
        if (!hasText(keyword)) {
            return "";
        }
        return Arrays.stream(keyword.split("\\s+"))
                .filter(token -> !DISCOUNT_KEYWORD_NOISE_WORDS.contains(normalizeVietnamese(token)))
                .collect(Collectors.joining(" "))
                .trim();
    }

    private boolean looksLikeSpecificDiscountQuestion(String normalizedMessage, String normalizedKeyword) {
        if (!hasText(normalizedMessage) || !hasText(normalizedKeyword)) {
            return false;
        }
        if (looksLikeGeneralDiscountQuestion(normalizedMessage, normalizedKeyword)) {
            return false;
        }
        return normalizedMessage.contains("co giam gia khong")
                || normalizedMessage.contains("co duoc giam gia khong")
                || normalizedMessage.contains("co sale khong")
                || normalizedMessage.contains("dang giam gia khong")
                || normalizedMessage.contains("co khuyen mai khong")
                || normalizedMessage.contains(normalizedKeyword);
    }

    private boolean looksLikeGeneralDiscountQuestion(String normalizedMessage, String normalizedKeyword) {
        if (!hasText(normalizedMessage)) {
            return false;
        }
        if (!hasText(normalizedKeyword)) {
            return true;
        }
        String keyword = " " + normalizedKeyword.trim() + " ";
        if (keyword.matches(".*\\b(tao|danh sach|liet ke|goi y|top|tat ca|nhung san pham|cac san pham)\\b.*")) {
            return true;
        }
        return normalizedMessage.matches(".*\\b(co gi|nhung gi|san pham nao|mat hang nao|hang nao|mon nao)\\b.*\\b(giam gia|sale|khuyen mai)\\b.*")
                || normalizedMessage.matches(".*\\b(danh sach|liet ke|goi y|top|tat ca|nhung san pham|cac san pham)\\b.*\\b(giam gia|sale|khuyen mai)\\b.*");
    }

    private List<ProductVariant> deduplicateVariantsByProduct(List<ProductVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductVariant> deduped = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            if (variant == null || variant.getProduct() == null) {
                continue;
            }
            deduped.putIfAbsent(variant.getProduct().getId(), variant);
        }
        return new ArrayList<>(deduped.values());
    }

    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        String decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd').replace('\u0110', 'D')
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
