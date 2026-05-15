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

@Node("User")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNode {

    @Id
    private Long userId; // Matches the JPA User ID

    private String fullName;

    @Builder.Default
    @Relationship(type = "HAS_GOAL", direction = Relationship.Direction.OUTGOING)
    private Set<DietaryGoalNode> dietaryGoals = new HashSet<>();

    @Builder.Default
    @Relationship(type = "PREFERS", direction = Relationship.Direction.OUTGOING)
    private Set<DietaryPreferenceNode> dietaryPreferences = new HashSet<>();

    @Builder.Default
    @Relationship(type = "HAS_CONDITION", direction = Relationship.Direction.OUTGOING)
    private Set<ConditionNode> conditions = new HashSet<>();
}
