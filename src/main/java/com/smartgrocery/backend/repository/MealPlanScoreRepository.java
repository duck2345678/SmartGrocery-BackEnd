package com.smartgrocery.backend.repository;
import com.smartgrocery.backend.entity.MealPlanScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MealPlanScoreRepository extends JpaRepository<MealPlanScore, Long> {}