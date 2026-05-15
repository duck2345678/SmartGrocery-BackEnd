package com.smartgrocery.backend.repository.graph;

import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.dto.response.ProductDictionaryProjection;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductNodeRepository extends Neo4jRepository<ProductNode, Long> {
    Optional<ProductNode> findByName(String name);

    /**
     * Fetch all products and their synonyms for the keyword dictionary.
     * Maps each synonym name to its product ID and product name.
     */
    @Query("MATCH (p:Product) " +
           "OPTIONAL MATCH (s:Synonym)-[:MAPS_TO]->(p) " +
           "RETURN p.productId as productId, p.name as productName, collect(s.name) as synonyms")
    List<ProductDictionaryProjection> findAllForDictionary();


    @Query("CALL db.index.fulltext.queryNodes('productFullTextIndex', $queryString) YIELD node, score RETURN node ORDER BY score DESC LIMIT 15")
    List<ProductNode> searchFullText(String queryString);

    /**
     * Alias cho searchFullText - dùng bởi AIController/AIService legacy.
     */
    @Query("CALL db.index.fulltext.queryNodes('productFullTextIndex', $keyword) YIELD node, score RETURN node ORDER BY score DESC LIMIT 15")
    List<ProductNode> searchByKeyword(String keyword);

    @Query("MATCH (p:Product) WHERE p.name =~ '(?i).*' + $keyword + '.*' RETURN p LIMIT 20")
    List<ProductNode> findByNameFuzzy(String keyword);

    @Query("MATCH (s:Synonym {name: $synonym})-[:MAPS_TO]->(p:Product) RETURN p LIMIT 10")
    List<ProductNode> findBySynonym(String synonym);

    @Query("MATCH (p:Product)-[:CONTAINS_INGREDIENT]->(i:Ingredient) WHERE i.name CONTAINS $ingredientName RETURN p")
    List<ProductNode> findByIngredient(String ingredientName);

    @Query("MATCH (p:Product)-[:BELONGS_TO]->(c:Category) WHERE c.name = $categoryName RETURN p")
    List<ProductNode> findByCategory(String categoryName);

    @Query("MATCH (p1:Product {productId: $productId})-[:BELONGS_TO]->(c:Category)<-[:BELONGS_TO]-(p2:Product) " +
            "WHERE p1 <> p2 RETURN p2 LIMIT $limit")
    List<ProductNode> findRelatedProducts(Long productId, int limit);

    @Query("MATCH (u:User {userId: $userId}) " +
            "OPTIONAL MATCH (u)-[:PREFERS]->(pref:DietaryPreference) " +
            "OPTIONAL MATCH (u)-[:HAS_GOAL]->(goal:DietaryGoal) " +
            "OPTIONAL MATCH (u)-[:HAS_CONDITION]->(cond:Condition) " +
            "MATCH (p:Product) " +
            "WHERE (pref IS NULL OR (p)-[:SUITABLE_FOR]->(pref)) " +
            "AND (goal IS NULL OR (p)-[:SUITABLE_FOR_GOAL]->(goal)) " +
            "AND (cond IS NULL OR NOT (p)-[:AVOID_FOR]->(cond)) " +
            "RETURN p LIMIT $limit")
    List<ProductNode> findRecommendedProductsForUser(Long userId, int limit);

    @Query("MATCH (u:User {userId: $userId}) " +
            "MATCH (p1:Product {productId: $productId})-[:BELONGS_TO]->(c:Category)<-[:BELONGS_TO]-(p2:Product) " +
            "OPTIONAL MATCH (u)-[:PREFERS]->(pref:DietaryPreference) " +
            "OPTIONAL MATCH (u)-[:HAS_GOAL]->(goal:DietaryGoal) " +
            "OPTIONAL MATCH (u)-[:HAS_CONDITION]->(cond:Condition) " +
            "WHERE p1 <> p2 " +
            "AND (pref IS NULL OR (p2)-[:SUITABLE_FOR]->(pref)) " +
            "AND (goal IS NULL OR (p2)-[:SUITABLE_FOR_GOAL]->(goal)) " +
            "AND (cond IS NULL OR NOT (p2)-[:AVOID_FOR]->(cond)) " +
            "RETURN DISTINCT p2 LIMIT $limit")
    List<ProductNode> findSubstitutionsForUserAndProduct(Long userId, Long productId, int limit);

    @Query("""
            MATCH (u:User {userId: $userId})
            MATCH (p1:Product {productId: $productId})
            MATCH (p2:Product)
            WHERE p1 <> p2
            OPTIONAL MATCH (p1)-[:BELONGS_TO]->(c1:Category)<-[:BELONGS_TO]-(p2)
            OPTIONAL MATCH (p1)-[:CONTAINS_INGREDIENT]->(ing:Ingredient)<-[:CONTAINS_INGREDIENT]-(p2)
            OPTIONAL MATCH (p1)-[sim:SIMILAR_TO]-(p2)
            OPTIONAL MATCH (u)-[:PREFERS]->(pref:DietaryPreference)
            OPTIONAL MATCH (u)-[:HAS_GOAL]->(goal:DietaryGoal)
            OPTIONAL MATCH (u)-[:HAS_CONDITION]->(cond:Condition)
            WITH p2,
                 (CASE WHEN c1 IS NULL THEN 0.0 ELSE 1.0 END) AS categoryScore,
                 count(DISTINCT ing) AS ingredientOverlap,
                 (CASE WHEN sim IS NULL THEN 0.0 ELSE 2.0 END) AS explicitSimilarityScore,
                 (CASE WHEN pref IS NULL OR (p2)-[:SUITABLE_FOR]->(pref) THEN 1.0 ELSE 0.0 END) AS prefScore,
                 (CASE WHEN goal IS NULL OR (p2)-[:SUITABLE_FOR_GOAL]->(goal) THEN 1.0 ELSE 0.0 END) AS goalScore,
                 (CASE WHEN cond IS NULL OR NOT (p2)-[:AVOID_FOR]->(cond) THEN 1.0 ELSE 0.0 END) AS safetyScore
            WHERE safetyScore > 0.0
            WITH p2,
                 (categoryScore * 2.0
                  + ingredientOverlap * 0.8
                  + explicitSimilarityScore
                  + prefScore
                  + goalScore) AS graphScore
            RETURN p2
            ORDER BY graphScore DESC
            LIMIT $limit
            """)
    List<ProductNode> findSimilarityCandidatesForUserAndProduct(Long userId, Long productId, int limit);

    @Query("""
            MATCH (u:User {userId: $userId})
            MATCH (p1:Product {productId: $productId})
            MATCH p = (p1)-[:SIMILAR_TO|CONTAINS_INGREDIENT|BELONGS_TO*2..3]-(p2:Product)
            WHERE p1 <> p2
            OPTIONAL MATCH (u)-[:PREFERS]->(pref:DietaryPreference)
            OPTIONAL MATCH (u)-[:HAS_GOAL]->(goal:DietaryGoal)
            OPTIONAL MATCH (u)-[:HAS_CONDITION]->(cond:Condition)
            WITH p2,
                 min(length(p)) AS minPath,
                 (CASE WHEN pref IS NULL OR (p2)-[:SUITABLE_FOR]->(pref) THEN 1.0 ELSE 0.0 END) AS prefScore,
                 (CASE WHEN goal IS NULL OR (p2)-[:SUITABLE_FOR_GOAL]->(goal) THEN 1.0 ELSE 0.0 END) AS goalScore,
                 (CASE WHEN cond IS NULL OR NOT (p2)-[:AVOID_FOR]->(cond) THEN 1.0 ELSE 0.0 END) AS safetyScore
            WHERE safetyScore > 0.0
            WITH p2, minPath,
                 ((4.0 / toFloat(minPath)) + prefScore + goalScore) AS multiHopScore
            RETURN p2
            ORDER BY multiHopScore DESC
            LIMIT $limit
            """)
    List<ProductNode> findMultiHopSubstitutionsForUserAndProduct(Long userId, Long productId, int limit);

    /**
     * Vector similarity search using Neo4j cosine distance.
     * 
     * Uses: VECTOR INDEX productVector on Product.embedding property
     * Returns: Products ranked by cosine similarity to query vector (highest first)
     * 
     * @param queryVector 768-dimensional embedding vector
     * @param threshold Minimum similarity score (0.0 to 1.0)
     * @param limit Maximum number of results
     * @return List of ProductNodes ranked by similarity descending
     */
    @Query("""
            MATCH (p:Product)
            WHERE p.embedding IS NOT NULL
            WITH p, gds.similarity.cosine(p.embedding, $queryVector) AS similarity
            WHERE similarity > $threshold
            RETURN p
            ORDER BY similarity DESC
            LIMIT $limit
            """)
    List<ProductNode> searchByVector(List<Double> queryVector, Double threshold, int limit);

    /**
     * Advanced vector search with built-in result filtering.
     * 
     * Filters products by:
     * - Minimum vector similarity
     * - Category (if specified)
     * - Availability (stock > 0)
     * 
     * @param queryVector Query embedding
     * @param threshold Similarity threshold
     * @param categoryId Optional category filter (null = no filter)
     * @param inStockOnly If true, only return products with stock > 0
     * @param limit Result limit
     * @return Filtered and ranked product list
     */
    @Query("""
            MATCH (p:Product)
            WHERE p.embedding IS NOT NULL
            WITH p, gds.similarity.cosine(p.embedding, $queryVector) AS similarity
            WHERE similarity > $threshold
            OPTIONAL MATCH (p)-[:BELONGS_TO]->(c:Category)
            WHERE $categoryId IS NULL OR c.categoryId = $categoryId
            OPTIONAL MATCH (p)-[:HAS_VARIANT]->(pv:ProductVariant)-[:HAS_STOCK]->(stock:InventoryStock)
            WITH p, similarity, 
                 CASE WHEN $inStockOnly THEN 
                   (CASE WHEN SUM(stock.availableQuantity) > 0 THEN 1 ELSE 0 END)
                 ELSE 1 END AS passesFilter
            WHERE passesFilter = 1
            RETURN p
            ORDER BY similarity DESC
            LIMIT $limit
            """)
    List<ProductNode> searchByVectorFiltered(
            List<Double> queryVector,
            Double threshold,
            Long categoryId,
            boolean inStockOnly,
            int limit
    );

    /**
     * Checks a list of product IDs against a user's conditions (allergies) to find conflicts.
     * Returns a list of ProductNodes that the user should AVOID.
     */
    @Query("""
            MATCH (u:User {userId: $userId})-[:HAS_CONDITION]->(c:Condition)
            MATCH (p:Product)-[:AVOID_FOR]->(c)
            WHERE p.productId IN $productIds
            RETURN DISTINCT p
            """)
    List<ProductNode> findConflictingProductsForUser(Long userId, List<Long> productIds);
}
