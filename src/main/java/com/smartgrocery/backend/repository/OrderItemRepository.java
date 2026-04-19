package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrder_Id(Long orderId);
    List<OrderItem> findByOrder_IdOrderByVariant_AisleLocationAsc(Long orderId);
}
