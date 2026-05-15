package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUser_Id(Long userId);

    // Alias used by CartService and OrderService
    default Optional<Cart> findByUserId(Long userId) {
        return findByUser_Id(userId);
    }
}
