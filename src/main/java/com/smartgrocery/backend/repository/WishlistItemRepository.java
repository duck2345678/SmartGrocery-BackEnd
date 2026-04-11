package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findByWishlist_Id(Long wishlistId);
    Optional<WishlistItem> findByWishlist_IdAndVariant_Id(Long wishlistId, Long variantId);
}
