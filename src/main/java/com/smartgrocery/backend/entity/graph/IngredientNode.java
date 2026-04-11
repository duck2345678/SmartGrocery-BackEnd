package com.smartgrocery.backend.entity.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Ingredient")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientNode {

    @Id
    private String name;
}
