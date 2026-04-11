package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser_Id(Long userId);
    Optional<Order> findByOrderNumber(String orderNumber);
}
