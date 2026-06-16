package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.InventoryStock;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    interface VariantStockSum {
        Long getVariantId();
        Long getTotalAvailable();
    }

    @Query("select s from InventoryStock s join fetch s.warehouse join fetch s.variant v join fetch v.product")
    List<InventoryStock> findAllWithRelations();

    @Query(value = """
            select s from InventoryStock s
            join fetch s.warehouse
            join fetch s.variant v
            join fetch v.product p
            where (:search is null
                   or lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(v.variantName) like lower(concat('%', :search, '%')))
            """,
            countQuery = """
            select count(s) from InventoryStock s
            join s.variant v
            join v.product p
            where (:search is null
                   or lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(v.variantName) like lower(concat('%', :search, '%')))
            """)
    Page<InventoryStock> findAllWithRelations(@Param("search") String search, Pageable pageable);

    @Query("select s from InventoryStock s join fetch s.warehouse join fetch s.variant v join fetch v.product where s.warehouse.id = :warehouseId")
    List<InventoryStock> findByWarehouseIdWithRelations(@Param("warehouseId") Long warehouseId);

    @Query(value = """
            select s from InventoryStock s
            join fetch s.warehouse
            join fetch s.variant v
            join fetch v.product p
            where s.warehouse.id = :warehouseId
              and (:search is null
                   or lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(v.variantName) like lower(concat('%', :search, '%')))
            """,
            countQuery = """
            select count(s) from InventoryStock s
            join s.variant v
            join v.product p
            where s.warehouse.id = :warehouseId
              and (:search is null
                   or lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(v.variantName) like lower(concat('%', :search, '%')))
            """)
    Page<InventoryStock> findByWarehouseIdWithRelations(@Param("warehouseId") Long warehouseId, @Param("search") String search, Pageable pageable);

    List<InventoryStock> findByWarehouseId(Long warehouseId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    Optional<InventoryStock> findByVariantId(Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    Optional<InventoryStock> findByWarehouseIdAndVariantId(Long warehouseId, Long variantId);

    @Query("select coalesce(sum(coalesce(s.availableQuantity, 0)), 0) from InventoryStock s where s.variant.id = :variantId")
    Long sumAvailableByVariantId(@Param("variantId") Long variantId);

    @Query("""
            select s.variant.id as variantId,
                   coalesce(sum(coalesce(s.availableQuantity, 0)), 0) as totalAvailable
            from InventoryStock s
            where s.variant.id in :variantIds
            group by s.variant.id
            """)
    List<VariantStockSum> sumAvailableByVariantIds(@Param("variantIds") List<Long> variantIds);

    List<InventoryStock> findAllByVariant_IdIn(List<Long> variantIds);

    @Query("select distinct s.variant.product.name from InventoryStock s where s.availableQuantity is null or s.availableQuantity <= 0")
    List<String> findOutOfStockProductNames();
}
