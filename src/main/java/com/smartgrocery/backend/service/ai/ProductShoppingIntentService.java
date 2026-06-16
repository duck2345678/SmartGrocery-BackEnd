package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.dto.ChatResponseDto;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductShoppingIntentService {

    private final ProductVariantRepository productVariantRepository;
    private final ShoppingItemBuilder shoppingItemBuilder;

    private static final Set<String> BUY_WORDS = Set.of("mua", "lay", "can", "tim", "kiem", "cho");
    private static final Set<String> BUY_NOISE_WORDS = Set.of(
            "toi", "minh", "em", "anh", "chi", "ban", "muon", "mua", "lay", "can",
            "tim", "kiem", "cho", "giup", "voi", "nhe", "nha", "a", "san", "pham",
            "mat", "hang", "mot", "it", "vai"
    );
    private static final Set<String> MEAL_WORDS = Set.of(
            "nau", "mon", "cong", "thuc", "nguyen", "lieu", "an", "lam", "che", "bien"
    );

    public record ProductShoppingResult(String reply, List<ChatResponseDto.ShoppingItem> shoppingItems) {
        public static ProductShoppingResult none() {
            return new ProductShoppingResult(null, null);
        }
    }

    public ProductShoppingResult detectProductShoppingIntent(String userMessage) {
        String keyword = extractProductKeyword(userMessage);
        if (keyword == null) {
            return ProductShoppingResult.none();
        }

        List<ProductVariant> variants = productVariantRepository.findTop10ActiveByKeyword(keyword);
        if (variants.isEmpty()) {
            variants = productVariantRepository.searchActiveForSubstitution(keyword);
        }
        List<ChatResponseDto.ShoppingItem> items = shoppingItemBuilder.buildShoppingItemsFromVariants(variants);
        if (items == null || items.isEmpty()) {
            return ProductShoppingResult.none();
        }

        return new ProductShoppingResult(
                String.format("Mình đã tìm thấy một vài sản phẩm phù hợp với \"%s\". Bạn xem danh sách bên dưới nhé.", keyword),
                items
        );
    }

    public String extractProductKeyword(String userMessage) {
        String normalized = normalizeVietnamese(userMessage);
        if (normalized.isBlank()) {
            return null;
        }
        List<String> tokens = Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();
        boolean hasBuyIntent = tokens.stream().anyMatch(BUY_WORDS::contains);
        boolean looksLikeMealIntent = tokens.stream().anyMatch(MEAL_WORDS::contains);
        if (!hasBuyIntent || looksLikeMealIntent) {
            return null;
        }
        String keyword = tokens.stream()
                .filter(token -> !BUY_NOISE_WORDS.contains(token))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return keyword.length() >= 2 ? keyword : null;
    }

    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd').replace('\u0110', 'D')
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
