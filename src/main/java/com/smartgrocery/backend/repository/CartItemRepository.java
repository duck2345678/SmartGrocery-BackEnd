package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCart_Id(Long cartId);
    Optional<CartItem> findByCart_IdAndVariant_Id(Long cartId, Long variantId);
    void deleteByCartId(Long cartId);
}
