package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.InventoryStock;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {
    List<InventoryStock> findByWarehouseId(Long warehouseId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    Optional<InventoryStock> findByVariantId(Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    Optional<InventoryStock> findByWarehouseIdAndVariantId(Long warehouseId, Long variantId);

    @Query("select coalesce(sum(coalesce(s.availableQuantity, 0)), 0) from InventoryStock s where s.variant.id = :variantId")
    Long sumAvailableByVariantId(@Param("variantId") Long variantId);
}
