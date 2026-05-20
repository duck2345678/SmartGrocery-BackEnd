package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.IngredientAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientAliasRepository extends JpaRepository<IngredientAlias, Long> {
    Optional<IngredientAlias> findFirstByAliasTextNormAndLangAndActiveTrue(String aliasTextNorm, String lang);
    List<IngredientAlias> findByCanonical_IdAndActiveTrue(Long canonicalId);
}
