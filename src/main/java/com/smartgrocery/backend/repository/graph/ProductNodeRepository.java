package com.smartgrocery.backend.repository.graph;

import com.smartgrocery.backend.entity.graph.ProductNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.neo4j.repository.query.Query;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductNodeRepository extends Neo4jRepository<ProductNode, Long> {
    Optional<ProductNode> findByName(String name);

    @Query("MATCH (p:Product) WHERE p.name CONTAINS $keyword RETURN p")
    List<ProductNode> searchByKeyword(String keyword);

    @Query("MATCH (p:Product)-[:CONTAINS_INGREDIENT]->(i:Ingredient) WHERE i.name CONTAINS $ingredientName RETURN p")
    List<ProductNode> findByIngredient(String ingredientName);

    @Query("MATCH (p:Product)-[:BELONGS_TO]->(c:Category) WHERE c.name = $categoryName RETURN p")
    List<ProductNode> findByCategory(String categoryName);

    @Query("MATCH (p1:Product {productId: $productId})-[:BELONGS_TO]->(c:Category)<-[:BELONGS_TO]-(p2:Product) " +
           "WHERE p1 <> p2 RETURN p2 LIMIT $limit")
    List<ProductNode> findRelatedProducts(Long productId, int limit);
}
