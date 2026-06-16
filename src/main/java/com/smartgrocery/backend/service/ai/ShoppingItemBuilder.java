package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.dto.ChatResponseDto;
import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShoppingItemBuilder {

    private final ProductVariantRepository productVariantRepository;

    public List<ChatResponseDto.ShoppingItem> buildShoppingItemsForMeal(
            Meal meal,
            Map<Long, List<MealIngredient>> ingredientsByMeal
    ) {
        List<MealIngredient> ingredients = ingredientsByMeal.getOrDefault(meal.getId(), List.of());
        if (ingredients.isEmpty()) return null;

        List<Long> productIds = ingredients.stream()
                .map(mi -> mi.getProduct().getId())
                .distinct()
                .collect(Collectors.toList());
        List<ProductVariant> variants = productVariantRepository.findByProduct_IdInAndStatus(productIds, "ACTIVE");
        Map<Long, ProductVariant> cheapestVariantByProduct = new HashMap<>();
        for (ProductVariant v : variants) {
            Long pid = v.getProduct().getId();
            if (!cheapestVariantByProduct.containsKey(pid) ||
                v.getNetPrice().compareTo(cheapestVariantByProduct.get(pid).getNetPrice()) < 0) {
                cheapestVariantByProduct.put(pid, v);
            }
        }

        List<ChatResponseDto.ShoppingItem> items = new ArrayList<>();
        for (MealIngredient mi : ingredients) {
            Product product = mi.getProduct();
            if (product == null || !"ACTIVE".equalsIgnoreCase(String.valueOf(product.getStatus()))) {
                continue;
            }
            ProductVariant variant = cheapestVariantByProduct.get(product.getId());
            if (variant == null) {
                continue;
            }
            items.add(ChatResponseDto.ShoppingItem.builder()
                    .productId(product.getId())
                    .variantId(variant.getId())
                    .name(product.getName())
                    .imageUrl(product.getImage())
                    .price(variant.getNetPrice())
                    .unit(variant.getUnit())
                    .role(mi.getRole())
                    .build());
        }
        log.info("[MealDetect] Built {} items for '{}'", items.size(), meal.getName());
        return items;
    }

    public List<ChatResponseDto.ShoppingItem> buildShoppingItemsFromVariants(List<ProductVariant> variants) {
        if (variants == null || variants.isEmpty()) return null;
        return variants.stream().map(v -> ChatResponseDto.ShoppingItem.builder()
                .productId(v.getProduct().getId())
                .variantId(v.getId())
                .name(v.getProduct().getName())
                .imageUrl(v.getProduct().getImage())
                .price(v.getNetPrice())
                .unit(v.getUnit())
                .role("PRODUCT")
                .build()).collect(Collectors.toList());
    }
}
