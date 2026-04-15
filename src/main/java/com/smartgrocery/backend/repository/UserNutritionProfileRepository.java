package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.UserNutritionProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserNutritionProfileRepository extends JpaRepository<UserNutritionProfile, Long> {
    Optional<UserNutritionProfile> findByUser_Id(Long userId);
}
