package com.smartgrocery.backend.repository;
import com.smartgrocery.backend.entity.BasketOptimizationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BasketOptimizationItemRepository extends JpaRepository<BasketOptimizationItem, Long> {}