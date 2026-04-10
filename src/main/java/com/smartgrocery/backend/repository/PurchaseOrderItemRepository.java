package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    List<PurchaseOrderItem> findByPurchaseOrder_Id(Long poId);

    // Alias used by PurchaseOrderService
    default List<PurchaseOrderItem> findByPurchaseOrderId(Long poId) {
        return findByPurchaseOrder_Id(poId);
    }
}
    