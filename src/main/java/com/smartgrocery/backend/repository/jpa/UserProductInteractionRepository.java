package com.smartgrocery.backend.repository.jpa;
import com.smartgrocery.backend.entity.UserProductInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProductInteractionRepository extends JpaRepository<UserProductInteraction, Long> {}
