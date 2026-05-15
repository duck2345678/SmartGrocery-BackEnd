package com.smartgrocery.backend.repository.jpa;
import com.smartgrocery.backend.entity.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {
    List<MealPlan> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
