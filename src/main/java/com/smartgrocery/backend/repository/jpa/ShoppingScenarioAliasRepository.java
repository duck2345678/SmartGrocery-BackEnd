package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.ShoppingScenarioAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShoppingScenarioAliasRepository extends JpaRepository<ShoppingScenarioAlias, Long> {

    @Query("""
            select a from ShoppingScenarioAlias a
            join fetch a.scenario s
            where s.active = true
            order by length(a.normalizedAlias) desc
    """)
    List<ShoppingScenarioAlias> findActiveAliases();
}
