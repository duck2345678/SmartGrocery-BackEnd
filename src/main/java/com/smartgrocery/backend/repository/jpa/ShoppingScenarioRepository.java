package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.ShoppingScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShoppingScenarioRepository extends JpaRepository<ShoppingScenario, String> {

    @Query("""
            select distinct s from ShoppingScenario s
            left join fetch s.items i
            where s.code = :code
              and s.active = true
            order by i.priority asc, i.id asc
    """)
    Optional<ShoppingScenario> findActiveByCodeWithItems(@Param("code") String code);
}
