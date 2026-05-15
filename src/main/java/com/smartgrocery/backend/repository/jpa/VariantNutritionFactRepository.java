package com.smartgrocery.backend.repository.jpa;
import com.smartgrocery.backend.entity.VariantNutritionFact;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VariantNutritionFactRepository extends JpaRepository<VariantNutritionFact, Long> {
    @Query("""
            select nf
            from VariantNutritionFact nf
            where nf.variant.product.id in :productIds
            """)
    List<VariantNutritionFact> findByProductIds(@Param("productIds") List<Long> productIds);
}
