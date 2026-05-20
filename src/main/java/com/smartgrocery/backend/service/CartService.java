package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AddToCartRequest;
import com.smartgrocery.backend.dto.CartDto;
import com.smartgrocery.backend.dto.CartItemDto;
import com.smartgrocery.backend.entity.Cart;
import com.smartgrocery.backend.entity.CartItem;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.CartItemRepository;
import com.smartgrocery.backend.repository.jpa.CartRepository;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import com.smartgrocery.backend.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class CartService {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    public CartDto getCart(Long userId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        Cart cart = getOrCreateCart(userId);
        return mapToDto(cart);
    }

    public CartDto addToCart(User user, AddToCartRequest request) {
        Cart cart = getOrCreateCart(user.getId());
        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new RuntimeException("Variant not found"));

        String source = normalizeSource(request.getSource());
        String aiListCode = normalizeAiListCode(request.getAiListCode());
        Optional<CartItem> existingItem = cartItemRepository.findByCart_IdAndVariant_IdAndSourceAndAiListCode(
                cart.getId(), variant.getId(), source, aiListCode);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            applyCartSourceMetadata(item, request);
            cartItemRepository.save(item);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(request.getQuantity())
                    .allowSubstitution(request.getAllowSubstitution() != null ? request.getAllowSubstitution() : false)
                    .source(source)
                    .aiListCode(aiListCode)
                    .aiListName(blankToNull(request.getAiListName()))
                    .build();
            cartItemRepository.save(newItem);
        }

        return getCart(user.getId());
    }

    /**
     * Batch add multiple items to cart in a single transaction.
     * Much faster than N sequential /add calls — used by AI chat "Add All" feature.
     */
    public CartDto batchAddToCart(User user, List<AddToCartRequest> requests) {
        Cart cart = getOrCreateCart(user.getId());
        List<Long> variantIds = requests.stream()
                .map(AddToCartRequest::getVariantId)
                .collect(Collectors.toList());
        Map<Long, ProductVariant> variantMap = productVariantRepository.findAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        for (AddToCartRequest request : requests) {
            ProductVariant variant = variantMap.get(request.getVariantId());
            if (variant == null) continue; // Skip unknown variants gracefully

            String source = normalizeSource(request.getSource());
            String aiListCode = normalizeAiListCode(request.getAiListCode());
            Optional<CartItem> existingItem = cartItemRepository.findByCart_IdAndVariant_IdAndSourceAndAiListCode(
                    cart.getId(), variant.getId(), source, aiListCode);
            if (existingItem.isPresent()) {
                CartItem item = existingItem.get();
                item.setQuantity(item.getQuantity() + request.getQuantity());
                applyCartSourceMetadata(item, request);
                cartItemRepository.save(item);
            } else {
                CartItem newItem = CartItem.builder()
                        .cart(cart)
                        .variant(variant)
                        .quantity(request.getQuantity())
                        .allowSubstitution(Boolean.TRUE.equals(request.getAllowSubstitution()))
                        .source(source)
                        .aiListCode(aiListCode)
                        .aiListName(blankToNull(request.getAiListName()))
                        .build();
                cartItemRepository.save(newItem);
            }
        }

        return getCart(user.getId());
    }

    public CartDto removeCartItem(User user, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));
        SecurityUtils.verifyResourceOwnerOrAdmin(item.getCart().getUser().getId(), "CartItem", cartItemId);
        cartItemRepository.deleteById(cartItemId);
        return getCart(user.getId());
    }

    public CartDto updateCartItem(User user, Long cartItemId, Integer quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu cập nhật");
        }
        if (quantity != null && quantity < 0) throw new IllegalArgumentException("quantity không hợp lệ");

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));
        SecurityUtils.verifyResourceOwnerOrAdmin(item.getCart().getUser().getId(), "CartItem", cartItemId);

        if (quantity != null) {
            if (quantity == 0) {
                cartItemRepository.deleteById(cartItemId);
                return getCart(user.getId());
            }
            item.setQuantity(quantity);
        }


        cartItemRepository.save(item);
        return getCart(user.getId());
    }

    public CartDto updateCartItemQuantity(User user, Long cartItemId, Integer quantity) {
        return updateCartItem(user, cartItemId, quantity);
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return cartRepository.save(Cart.builder().user(user).build());
        });
    }

    private CartDto mapToDto(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCart_Id(cart.getId());
        BigDecimal totalAmount = items.stream()
                .map(item -> item.getVariant().getNetPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartDto.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .items(items.stream().map(this::mapItemToDto).collect(Collectors.toList()))
                .totalAmount(totalAmount)
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    private CartItemDto mapItemToDto(CartItem item) {
        BigDecimal subtotal = item.getVariant().getNetPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        // Fix: call once, avoid duplicate DB query
        Long stockRaw = inventoryStockRepository.sumAvailableByVariantId(item.getVariant().getId());
        Integer stock = stockRaw != null ? stockRaw.intValue() : 0;
        return CartItemDto.builder()
                .id(item.getId())
                .productId(item.getVariant().getProduct().getId())
                .variantId(item.getVariant().getId())
                .variantName(item.getVariant().getVariantName())
                .unit(item.getVariant().getUnit())
                .productName(item.getVariant().getProduct().getName())
                .sku(item.getVariant().getSku())
                .unitPrice(item.getVariant().getNetPrice())
                .compareAtPrice(item.getVariant().getCompareAtPrice())
                .quantity(item.getQuantity())
                .subtotal(subtotal)
                .imageUrl(item.getVariant().getProduct().getImage())
                .stock(stock)
                .addedAt(item.getAddedAt())
                .allowSubstitution(item.getAllowSubstitution())
                .source(item.getSource())
                .aiListCode(item.getAiListCode())
                .aiListName(item.getAiListName())
                .build();
    }

    private void applyCartSourceMetadata(CartItem item, AddToCartRequest request) {
        String source = normalizeSource(request.getSource());
        if ("AI".equals(source)) {
            item.setSource(source);
            item.setAiListCode(normalizeAiListCode(request.getAiListCode()));
            item.setAiListName(blankToNull(request.getAiListName()));
        }
    }

    private String normalizeSource(String source) {
        return "AI".equalsIgnoreCase(source) ? "AI" : "MANUAL";
    }

    private String normalizeAiListCode(String code) {
        String value = blankToNull(code);
        return value != null ? value.substring(0, Math.min(value.length(), 80)) : "";
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
