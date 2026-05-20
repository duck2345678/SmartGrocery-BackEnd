package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MealRepository extends JpaRepository<Meal, Long> {

    @Query("SELECT m FROM Meal m WHERE " +
           "(:category IS NULL OR m.category = :category) AND " +
           "(:goal IS NULL OR m.dietaryGoal = :goal)")
    List<Meal> findByFilters(@Param("category") String category, @Param("goal") String goal);

    List<Meal> findByCategory(String category);
}
