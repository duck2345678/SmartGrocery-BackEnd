package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCart_Id(Long cartId);
    List<CartItem> findByVariant_Product_Id(Long productId);
    List<CartItem> findByCart(com.smartgrocery.backend.entity.Cart cart);
    Optional<CartItem> findByCart_IdAndVariant_Id(Long cartId, Long variantId);
    void deleteByCartId(Long cartId);
}
