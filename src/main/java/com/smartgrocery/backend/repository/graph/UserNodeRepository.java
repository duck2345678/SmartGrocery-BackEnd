package com.smartgrocery.backend.repository.graph;

import com.smartgrocery.backend.entity.graph.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, Long> {
    Optional<UserNode> findByUserId(Long userId);
}
