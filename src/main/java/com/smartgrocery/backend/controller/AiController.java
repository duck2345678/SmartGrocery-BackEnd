package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AINudgeDto;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.WishlistItem;
import com.smartgrocery.backend.repository.jpa.WishlistItemRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Controller", description = "API cho các gợi ý AI thông minh")
public class AiController {

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Operation(summary = "Gợi ý sản phẩm trong Wishlist đang giảm giá")
    @GetMapping("/nudges")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AINudgeDto>> getNudges(
            @AuthenticationPrincipal User user
    ) {
        if (user == null || user.getId() == null) {
            return ResponseEntity.ok(List.of());
        }

        // Find all wishlist items for this user (via Wishlist -> WishlistItem -> Product)
        List<WishlistItem> wishlistItems = wishlistItemRepository.findByWishlist_UserId(user.getId());
        if (wishlistItems.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<AINudgeDto> candidates = new ArrayList<>();

        for (WishlistItem wi : wishlistItems) {
            if (wi.getProduct() == null) continue;
            var product = wi.getProduct();

            // Skip non-active products
            if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) continue;

            // Check variants for any with a discount (compareAtPrice > netPrice)
            List<ProductVariant> variants = product.getVariants();
            if (variants == null || variants.isEmpty()) continue;

            for (ProductVariant variant : variants) {
                if (!"ACTIVE".equalsIgnoreCase(variant.getStatus())) continue;
                if (variant.getCompareAtPrice() == null || variant.getNetPrice() == null) continue;

                // Only include if there's an actual discount
                if (variant.getCompareAtPrice().compareTo(variant.getNetPrice()) > 0) {
                    BigDecimal originalPrice = variant.getCompareAtPrice();
                    BigDecimal salePrice = variant.getNetPrice();
                    int discountPercent = originalPrice.subtract(salePrice)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(originalPrice, 0, RoundingMode.HALF_UP)
                            .intValue();

                    String reason = String.format("Giảm %d%% — từ %sđ còn %sđ!",
                            discountPercent,
                            originalPrice.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                            salePrice.setScale(0, RoundingMode.HALF_UP).toPlainString());

                    candidates.add(AINudgeDto.builder()
                            .productId(product.getId())
                            .name(product.getName())
                            .image(product.getImage())
                            .price(salePrice)
                            .reason(reason)
                            .confidenceScore((double) discountPercent / 100.0)
                            .build());

                    break; // Only take the first discounted variant per product
                }
            }
        }

        // Sort by discount percentage (highest first)
        candidates.sort(Comparator.comparing(AINudgeDto::getConfidenceScore, Comparator.nullsLast(Comparator.reverseOrder())));
        List<AINudgeDto> result = candidates.stream().limit(5).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
