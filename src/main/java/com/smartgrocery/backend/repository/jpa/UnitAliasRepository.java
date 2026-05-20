package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.UnitAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitAliasRepository extends JpaRepository<UnitAlias, Long> {
    Optional<UnitAlias> findFirstByAliasTextNormAndLocaleAndActiveTrue(String aliasTextNorm, String locale);
    List<UnitAlias> findByUnitCanonical_IdAndActiveTrue(Long unitCanonicalId);
}
