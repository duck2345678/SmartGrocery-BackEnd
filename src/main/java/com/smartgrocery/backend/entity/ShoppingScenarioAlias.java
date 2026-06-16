package com.smartgrocery.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shopping_scenario_alias")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShoppingScenarioAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_code", nullable = false)
    private ShoppingScenario scenario;

    @Column(name = "alias_text", nullable = false, length = 150)
    private String aliasText;

    @Column(name = "normalized_alias", nullable = false, length = 150)
    private String normalizedAlias;
}
