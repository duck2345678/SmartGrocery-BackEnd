package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.MealIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MealIngredientRepository extends JpaRepository<MealIngredient, Long> {
    List<MealIngredient> findByMealId(Long mealId);

    @Query("""
            select mi
            from MealIngredient mi
            left join fetch mi.product p
            left join fetch mi.canonicalIngredient ci
            left join fetch mi.quantityUnitCanonical qu
            where mi.meal.id = :mealId
            order by mi.id asc
            """)
    List<MealIngredient> findByMealIdWithProduct(@Param("mealId") Long mealId);

    /**
     * Batch load ALL meal ingredients across ALL meals in ONE query.
     * Replaces the N+1 loop in AiChatController (was 114 separate queries).
     */
    @Query("""
            select mi
            from MealIngredient mi
            left join fetch mi.meal m
            left join fetch mi.product p
            left join fetch mi.canonicalIngredient ci
            left join fetch mi.quantityUnitCanonical qu
            order by mi.meal.id asc, mi.id asc
            """)
    List<MealIngredient> findAllWithProduct();

    @Query("""
            select mi
            from MealIngredient mi
            left join fetch mi.product p
            left join fetch mi.canonicalIngredient ci
            left join fetch mi.quantityUnitCanonical qu
            where mi.id in :ids
            """)
    List<MealIngredient> findByIdsWithCanonicalAndUnit(@Param("ids") List<Long> ids);

    @Query("""
            select distinct coalesce(mi.genericName, p.name)
            from MealIngredient mi
            left join mi.product p
            where coalesce(mi.genericName, p.name) is not null
              and trim(coalesce(mi.genericName, p.name)) <> ''
            """)
    List<String> findDistinctIngredientSourceNames();
}
