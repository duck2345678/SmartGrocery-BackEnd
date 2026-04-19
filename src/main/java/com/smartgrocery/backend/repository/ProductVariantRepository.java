package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
