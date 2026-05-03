package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findBySku(String sku);
    Optional<ProductVariant> findByBarcode(String barcode);
    List<ProductVariant> findByProduct_Id(Long productId);

    List<ProductVariant> findTop50ByProduct_Category_IdAndStatusAndNetPriceLessThanEqualOrderByNetPriceDesc(
            Long categoryId,
            String status,
            BigDecimal maxNetPrice
    );

    @Query("""
            select v from ProductVariant v
            where v.status = 'ACTIVE'
              and (
                lower(v.product.name) like lower(concat('%', :keyword, '%'))
                or lower(v.sku) like lower(concat('%', :keyword, '%'))
              )
            order by v.updatedAt desc
            """)
    List<ProductVariant> searchActiveForSubstitution(@Param("keyword") String keyword);
}
