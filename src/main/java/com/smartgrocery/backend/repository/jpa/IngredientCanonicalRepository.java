package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.IngredientCanonical;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientCanonicalRepository extends JpaRepository<IngredientCanonical, Long> {
    Optional<IngredientCanonical> findByCanonicalCode(String canonicalCode);
    List<IngredientCanonical> findByActiveTrue();
}
