package com.smartgrocery.backend.entity.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("DietaryGoal")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DietaryGoalNode {

    @Id
    private String name; // e.g., "Weight Loss", "Muscle Gain", "Maintenance"
    
    private String description;
}
