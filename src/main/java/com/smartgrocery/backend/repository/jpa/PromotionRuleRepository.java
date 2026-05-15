package com.smartgrocery.backend.repository.jpa;
import com.smartgrocery.backend.entity.PromotionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionRuleRepository extends JpaRepository<PromotionRule, Long> {}
