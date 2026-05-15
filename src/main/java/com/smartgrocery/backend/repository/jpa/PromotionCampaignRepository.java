package com.smartgrocery.backend.repository.jpa;
import com.smartgrocery.backend.entity.PromotionCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, Long> {}
