package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.Cart;
import com.smartgrocery.backend.entity.CartItem;
import com.smartgrocery.backend.entity.UserNutritionProfile;
import com.smartgrocery.backend.entity.VariantNutritionFact;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.jpa.CartItemRepository;
import com.smartgrocery.backend.repository.jpa.CartRepository;
import com.smartgrocery.backend.repository.jpa.UserNutritionProfileRepository;
import com.smartgrocery.backend.repository.jpa.VariantNutritionFactRepository;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartInspectionService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserNutritionProfileRepository nutritionProfileRepository;
    private final VariantNutritionFactRepository nutritionFactRepository;
    private final ProductNodeRepository productNodeRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String CART_INSPECTION_CACHE_PREFIX = "cart:inspect:";

    @Data
    @Builder
    public static class CartInspectionReport {
        private String cartHash;
        private boolean hasConflicts;
        private List<String> warnings;
        private List<Long> conflictingVariantIds;
        private BigDecimal totalCalories;
        private BigDecimal totalProtein;
        private String formattedPromptText;
    }

    /**
     * Quét giỏ hàng: Tính toán macros, kiểm tra dị ứng (Neo4j), và cảnh báo.
     * Sử dụng Redis để debounce (không quét lại nếu giỏ hàng không đổi).
     */
    public CartInspectionReport inspectCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return CartInspectionReport.builder()
                    .hasConflicts(false)
                    .warnings(List.of())
                    .formattedPromptText("Giỏ hàng đang trống.")
                    .build();
        }
        
        List<CartItem> cartItems = cartItemRepository.findByCart_Id(cart.getId());
        if (cartItems.isEmpty()) {
            return CartInspectionReport.builder()
                    .hasConflicts(false)
                    .warnings(List.of())
                    .formattedPromptText("Giỏ hàng đang trống.")
                    .build();
        }

        // 1. Generate State Hash cho Debounce
        String cartState = cartItems.stream()
                .sorted(Comparator.comparing(item -> item.getVariant().getId()))
                .map(item -> item.getVariant().getId() + ":" + item.getQuantity())
                .collect(Collectors.joining("|"));
        String hash = DigestUtils.md5DigestAsHex(cartState.getBytes());

        String cacheKey = CART_INSPECTION_CACHE_PREFIX + userId;
        String cachedHash = redisTemplate.opsForValue().get(cacheKey);

        if (hash.equals(cachedHash)) {
            log.debug("Cart state unchanged (hash: {}), proceeding with re-evaluation from memory/DB quickly.", hash);
        } else {
            log.info("New cart state detected for user {}, running deep inspection...", userId);
            redisTemplate.opsForValue().set(cacheKey, hash, 1, TimeUnit.HOURS);
        }

        // 2. Phân tích Dinh dưỡng tổng (Macros)
        Map<Long, VariantNutritionFact> nutritionMap = nutritionFactRepository.findByProductIds(
                cartItems.stream().map(i -> i.getVariant().getProduct().getId()).toList()
        ).stream().collect(Collectors.toMap(nf -> nf.getVariant().getId(), nf -> nf));

        BigDecimal totalCalories = BigDecimal.ZERO;
        BigDecimal totalProtein = BigDecimal.ZERO;

        for (CartItem item : cartItems) {
            VariantNutritionFact nf = nutritionMap.get(item.getVariant().getId());
            if (nf != null) {
                BigDecimal qty = BigDecimal.valueOf(item.getQuantity());
                if (nf.getCaloriesPer100g() != null) {
                    totalCalories = totalCalories.add(nf.getCaloriesPer100g().multiply(qty));
                }
                if (nf.getProteinPer100g() != null) {
                    totalProtein = totalProtein.add(nf.getProteinPer100g().multiply(qty));
                }
            }
        }

        // 3. Phân tích Neo4j Constraints (Dị ứng, Condition)
        List<String> warnings = new ArrayList<>();
        List<Long> conflictingVariantIds = new ArrayList<>();

        Optional<UserNutritionProfile> profileOpt = nutritionProfileRepository.findByUser_Id(userId);
        
        List<Long> productIdsInCart = cartItems.stream()
                .map(item -> item.getVariant().getProduct().getId())
                .distinct()
                .toList();

        if (!productIdsInCart.isEmpty()) {
            List<ProductNode> conflictingNodes = productNodeRepository.findConflictingProductsForUser(userId, productIdsInCart);
            if (!conflictingNodes.isEmpty()) {
                warnings.add("NGUY HIỂM: Giỏ hàng chứa sản phẩm gây dị ứng hoặc xung đột với hồ sơ y tế của bạn!");
                for (ProductNode p : conflictingNodes) {
                    warnings.add("Tránh mua: " + p.getName());
                    
                    List<Long> varIds = cartItems.stream()
                            .filter(i -> i.getVariant().getProduct().getId().equals(p.getProductId()))
                            .map(i -> i.getVariant().getId())
                            .toList();
                    conflictingVariantIds.addAll(varIds);
                }
            }
        }
            
        if (profileOpt.isPresent()) {
            UserNutritionProfile p = profileOpt.get();
            if (p.getDailyCalorieTarget() != null && totalCalories.compareTo(BigDecimal.valueOf(p.getDailyCalorieTarget())) > 0) {
                if (!warnings.contains("CẢNH BÁO: Giỏ hàng vượt quá mục tiêu Calo trong ngày!")) {
                    warnings.add("CẢNH BÁO: Giỏ hàng vượt quá mục tiêu Calo trong ngày!");
                }
            }
        }

        boolean hasConflicts = !warnings.isEmpty() || !conflictingVariantIds.isEmpty();

        StringBuilder promptText = new StringBuilder("BÁO CÁO GIỎ HÀNG THỰC TẾ (CART INSPECTION):\n");
        promptText.append("- Tổng Calo ước tính: ").append(totalCalories.setScale(0, RoundingMode.HALF_UP)).append(" kcal\n");
        promptText.append("- Tổng Protein: ").append(totalProtein.setScale(1, RoundingMode.HALF_UP)).append(" g\n");
        
        promptText.append("\nCHI TIẾT GIỎ HÀNG THỰC TẾ (Sản phẩm đã có):\n");
        for (CartItem item : cartItems) {
            promptText.append(String.format("- %s | Số lượng: %d\n", 
                item.getVariant().getProduct().getName(), item.getQuantity()));
        }
        
        if (hasConflicts) {
            promptText.append("\n⚠️ PHÁT HIỆN RỦI RO SỨC KHỎE:\n");
            for (String w : warnings) {
                promptText.append("- ").append(w).append("\n");
            }
            if (!conflictingVariantIds.isEmpty()) {
                promptText.append("\n[CRITICAL_CONFLICT_VARIANTS: ").append(conflictingVariantIds).append("]\n");
            }
        } else {
            promptText.append("\n✅ Giỏ hàng an toàn, phù hợp với mục tiêu dinh dưỡng.\n");
        }

        return CartInspectionReport.builder()
                .cartHash(hash)
                .hasConflicts(hasConflicts)
                .warnings(warnings)
                .conflictingVariantIds(conflictingVariantIds)
                .totalCalories(totalCalories)
                .totalProtein(totalProtein)
                .formattedPromptText(promptText.toString())
                .build();
    }
}
