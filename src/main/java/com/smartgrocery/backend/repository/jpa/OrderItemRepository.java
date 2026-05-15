package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrder_Id(Long orderId);
    
    @Query("SELECT oi FROM OrderItem oi LEFT JOIN FETCH oi.variant v LEFT JOIN FETCH v.product p WHERE oi.order.id = :orderId")
    List<OrderItem> findByOrder_IdWithDetails(@Param("orderId") Long orderId);


    @Query("""
           select oi.variant.product.id, coalesce(sum(oi.quantity), 0)
           from OrderItem oi
           where oi.variant.product.id in :productIds
             and oi.order.status <> 'CANCELLED'
           group by oi.variant.product.id
           """)
    List<Object[]> sumPurchasedQuantityByProductIds(@Param("productIds") List<Long> productIds);
}
