package com.smartgrocery.backend.repository;
import com.smartgrocery.backend.entity.BasketOptimization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BasketOptimizationRepository extends JpaRepository<BasketOptimization, Long> {}