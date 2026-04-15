package com.smartgrocery.backend.repository;
import com.smartgrocery.backend.entity.VariantNutritionFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VariantNutritionFactRepository extends JpaRepository<VariantNutritionFact, Long> {}