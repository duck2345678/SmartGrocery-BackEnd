package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.UnitCanonical;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitCanonicalRepository extends JpaRepository<UnitCanonical, Long> {
    Optional<UnitCanonical> findByUnitCode(String unitCode);
    List<UnitCanonical> findByActiveTrue();
}
