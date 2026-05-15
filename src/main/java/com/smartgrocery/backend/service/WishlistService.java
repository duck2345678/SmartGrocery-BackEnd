package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.ProductDto;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.Wishlist;
import com.smartgrocery.backend.entity.WishlistItem;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.WishlistRepository;
import com.smartgrocery.backend.repository.jpa.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    @Transactional
    public Wishlist getOrCreateWishlist(User user) {
        return wishlistRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Wishlist wishlist = Wishlist.builder()
                            .user(user)
                            .build();
                    return wishlistRepository.save(wishlist);
                });
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getWishlist(User user) {
        Wishlist wishlist = wishlistRepository.findByUserId(user.getId()).orElse(null);
        if (wishlist == null) return new ArrayList<>();

        List<WishlistItem> items = wishlistItemRepository.findByWishlist_Id(wishlist.getId());
        List<Long> productIds = items.stream().map(i -> i.getProduct().getId()).toList();
        
        return productIds.stream()
                .map(id -> productService.getProductById(id))
                .toList();
    }

    @Transactional
    public void addToWishlist(User user, Long productId) {
        Wishlist wishlist = getOrCreateWishlist(user);
        
        if (wishlistItemRepository.findByWishlist_UserIdAndProductId(user.getId(), productId).isPresent()) {
            return;
        }
        
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        
        WishlistItem item = WishlistItem.builder()
                .wishlist(wishlist)
                .product(product)
                .build();
        wishlistItemRepository.save(item);
    }

    @Transactional
    public void removeFromWishlist(User user, Long productId) {
        wishlistItemRepository.deleteByWishlist_UserIdAndProductId(user.getId(), productId);
    }

    @Transactional(readOnly = true)
    public boolean isInWishlist(User user, Long productId) {
        return wishlistItemRepository.findByWishlist_UserIdAndProductId(user.getId(), productId).isPresent();
    }
}
