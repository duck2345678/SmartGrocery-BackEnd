package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByUser_IdOrderByScoreDesc(Long userId);
}
