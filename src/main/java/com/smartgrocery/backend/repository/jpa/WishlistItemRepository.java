package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findByWishlist_Id(Long wishlistId);
    List<WishlistItem> findByProduct_Id(Long productId);
    Optional<WishlistItem> findByWishlist_UserIdAndProductId(Long userId, Long productId);
    void deleteByWishlist_UserIdAndProductId(Long userId, Long productId);
}
