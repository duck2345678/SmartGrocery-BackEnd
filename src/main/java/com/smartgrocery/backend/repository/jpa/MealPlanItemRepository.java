package com.smartgrocery.backend.repository.jpa;
import com.smartgrocery.backend.entity.MealPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MealPlanItemRepository extends JpaRepository<MealPlanItem, Long> {}
