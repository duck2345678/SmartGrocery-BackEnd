package com.smartgrocery.backend.repository;
import com.smartgrocery.backend.entity.RecommendationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationRunRepository extends JpaRepository<RecommendationRun, Long> {}