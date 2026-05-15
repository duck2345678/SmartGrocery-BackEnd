package com.smartgrocery.backend.repository.jpa;
import com.smartgrocery.backend.entity.AIModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AIModelRepository extends JpaRepository<AIModel, Long> {}
