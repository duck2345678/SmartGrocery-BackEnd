package com.smartgrocery.backend.entity.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("Product")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductNode {

    @Id
    private Long productId;

    private String name;
    private String productCode;
    private String status;
    private Double price;
    private String unit;
    private String description;
    private String shortDescription;
    private String categoryName;
    private String originCountry;
    private String variantSkus;
    private String variantNames;
    private String familyTags;
    private String searchText;
    private Integer stock;
    private Boolean hasStock;
    private String updatedAt;
    
    @Builder.Default
    private Boolean isStaple = false;

    @Relationship(type = "BELONGS_TO", direction = Relationship.Direction.OUTGOING)
    private CategoryNode category;

    @Builder.Default
    @Relationship(type = "CONTAINS_INGREDIENT", direction = Relationship.Direction.OUTGOING)
    private Set<IngredientNode> ingredients = new HashSet<>();

    @Builder.Default
    @Relationship(type = "SUITABLE_FOR", direction = Relationship.Direction.OUTGOING)
    private Set<DietaryPreferenceNode> suitableForPreferences = new HashSet<>();

    @Builder.Default
    @Relationship(type = "SUITABLE_FOR_GOAL", direction = Relationship.Direction.OUTGOING)
    private Set<DietaryGoalNode> suitableForGoals = new HashSet<>();

    @Builder.Default
    @Relationship(type = "AVOID_FOR", direction = Relationship.Direction.OUTGOING)
    private Set<ConditionNode> avoidForConditions = new HashSet<>();
}
