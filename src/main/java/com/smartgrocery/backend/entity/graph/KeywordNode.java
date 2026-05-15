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

@Node("Keyword")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeywordNode {

    @Id
    private String name;

    @Builder.Default
    @Relationship(type = "MAPS_TO_GOAL", direction = Relationship.Direction.OUTGOING)
    private Set<DietaryGoalNode> goals = new HashSet<>();

    @Builder.Default
    @Relationship(type = "MAPS_TO_PREF", direction = Relationship.Direction.OUTGOING)
    private Set<DietaryPreferenceNode> preferences = new HashSet<>();
}
