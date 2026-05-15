package com.smartgrocery.backend.entity.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("DietaryPreference")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DietaryPreferenceNode {

    @Id
    private String name; // e.g., "Vegetarian", "Vegan", "Keto", "Halal"

    private String description;
}
