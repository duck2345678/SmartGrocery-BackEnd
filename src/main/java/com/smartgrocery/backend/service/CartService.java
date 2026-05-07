package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AddToCartRequest;
import com.smartgrocery.backend.dto.CartDto;
import com.smartgrocery.backend.dto.CartItemDto;
import com.smartgrocery.backend.entity.Cart;
import com.smartgrocery.backend.entity.CartItem;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.CartItemRepository;
import com.smartgrocery.backend.repository.CartRepository;
import com.smartgrocery.backend.repository.InventoryStockRepository;
import com.smartgrocery.backend.repository.ProductVariantRepository;
import com.smartgrocery.backend.repository.UserRepository;
import com.smartgrocery.backend.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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

        Optional<CartItem> existingItem = cartItemRepository.findByCart_IdAndVariant_Id(cart.getId(), variant.getId());

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            cartItemRepository.save(item);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(request.getQuantity())
                    .build();
            cartItemRepository.save(newItem);
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
        Integer stock = inventoryStockRepository.sumAvailableByVariantId(item.getVariant().getId()) != null
                ? inventoryStockRepository.sumAvailableByVariantId(item.getVariant().getId()).intValue()
                : 0;
        return CartItemDto.builder()
                .id(item.getId())
                .productId(item.getVariant().getProduct().getId())
                .variantId(item.getVariant().getId())
                .variantName(item.getVariant().getVariantName())
                .unit(item.getVariant().getUnit())
                .productName(item.getVariant().getProduct().getName())
                .sku(item.getVariant().getSku())
                .unitPrice(item.getVariant().getNetPrice())
                .quantity(item.getQuantity())
                .subtotal(subtotal)
                .imageUrl(item.getVariant().getProduct().getImage())
                .stock(stock)
                .addedAt(item.getAddedAt())
                .build();
    }
}
