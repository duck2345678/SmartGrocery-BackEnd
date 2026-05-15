package com.smartgrocery.backend.entity.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Condition")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConditionNode {

    @Id
    private String name; // e.g., "Diabetes", "Peanut Allergy", "Lactose Intolerance"

    private String description;
}
