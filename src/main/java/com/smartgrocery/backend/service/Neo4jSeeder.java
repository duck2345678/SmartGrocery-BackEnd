package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.entity.graph.UserNode;
import com.smartgrocery.backend.entity.graph.DietaryPreferenceNode;
import com.smartgrocery.backend.entity.graph.DietaryGoalNode;
import com.smartgrocery.backend.entity.graph.ConditionNode;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import com.smartgrocery.backend.repository.graph.UserNodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class Neo4jSeeder {

    @Autowired
    private ProductRepository jpaProductRepository;

    @Autowired
    private UserRepository jpaUserRepository;

    @Autowired
    private ProductVariantRepository jpaProductVariantRepository;

    @Autowired
    private ProductNodeRepository productNodeRepository;

    @Autowired
    private UserNodeRepository userNodeRepository;

    @Autowired
    private org.springframework.data.neo4j.core.Neo4jClient neo4jClient;

    @Value("${app.neo4j.legacy-seed.enabled:false}")
    private boolean legacySeedEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void seedNeo4j() {
        if (!legacySeedEnabled) {
            log.info("Legacy Neo4jSeeder disabled. Use Neo4jCatalogRebuildService for catalog sync.");
            return;
        }
        try {
            log.info("Starting Neo4j Seeding...");
            
            // 0. Khởi tạo Schema / Relationship Types để tránh UnknownRelationshipTypeWarning
            try {
                neo4jClient.query("CREATE FULLTEXT INDEX productFullTextIndex IF NOT EXISTS FOR (p:Product) ON EACH [p.name, p.description]").run();
                
                // Ép Neo4j nhận diện các Relationship Types quan trọng
                log.info("Initializing relationship types in Neo4j...");
                neo4jClient.query("""
                    MERGE (u:User {userId: 0})
                    MERGE (p:DietaryPreference {name: 'Init'})
                    MERGE (g:DietaryGoal {name: 'Init'})
                    MERGE (c:Condition {name: 'Init'})
                    MERGE (u)-[:PREFERS]->(p)
                    MERGE (u)-[:HAS_GOAL]->(g)
                    MERGE (u)-[:HAS_CONDITION]->(c)
                    WITH u, p, g, c
                    MATCH (u)-[r1:PREFERS]->(p), (u)-[r2:HAS_GOAL]->(g), (u)-[r3:HAS_CONDITION]->(c)
                    DELETE r1, r2, r3, u, p, g, c
                """).run();
                
                log.info("Successfully ensured productFullTextIndex and relationship types");
            } catch (Exception ex) {
                log.warn("Could not initialize schema/indexes: {}", ex.getMessage());
            }

            // Seed JPA Users to Neo4j
            List<User> users = jpaUserRepository.findAll();
            for (User user : users) {
                Optional<UserNode> existing = userNodeRepository.findByUserId(user.getId());
                if (existing.isEmpty()) {
                    UserNode node = UserNode.builder()
                            .userId(user.getId())
                            .fullName(user.getFullName())
                            .build();
                            
                    // Tạo dữ liệu Graph cơ bản để Neo4j tạo Label và Relationship
                    DietaryPreferenceNode vegPref = DietaryPreferenceNode.builder().name("Vegetarian").description("Ăn chay").build();
                    DietaryGoalNode muscleGain = DietaryGoalNode.builder().name("Muscle Gain").description("Tăng cơ").build();
                    ConditionNode lactose = ConditionNode.builder().name("Lactose Intolerance").description("Dị ứng sữa").build();

                    node.getDietaryPreferences().add(vegPref);
                    node.getDietaryGoals().add(muscleGain);
                    node.getConditions().add(lactose);

                    userNodeRepository.save(node);
                    log.info("Seeded UserNode: {}", user.getId());
                }
            }

            // Seed JPA Products to Neo4j
            List<Product> products = jpaProductRepository.findAll();
            for (Product product : products) {
                Optional<ProductNode> existing = productNodeRepository.findById(product.getId());
                if (existing.isEmpty()) {
                    List<ProductVariant> variants = jpaProductVariantRepository.findByProduct_Id(product.getId());
                    double price = 0.0;
                    if (!variants.isEmpty() && variants.get(0).getNetPrice() != null) {
                        price = variants.get(0).getNetPrice().doubleValue();
                    }

                    ProductNode node = ProductNode.builder()
                            .productId(product.getId())
                            .name(product.getName())
                            .description(product.getDescription())
                            .price(price)
                            .build();

                    // Map Graph relationships cho Product
                    DietaryPreferenceNode vegPref = DietaryPreferenceNode.builder().name("Vegetarian").description("Ăn chay").build();
                    DietaryGoalNode muscleGain = DietaryGoalNode.builder().name("Muscle Gain").description("Tăng cơ").build();
                    ConditionNode lactose = ConditionNode.builder().name("Lactose Intolerance").description("Dị ứng sữa").build();

                    node.getSuitableForPreferences().add(vegPref);
                    node.getSuitableForGoals().add(muscleGain);
                    
                    // Giả lập một số sản phẩm cần tránh cho người dị ứng sữa
                    if (product.getId() % 3 == 0) {
                        node.getAvoidForConditions().add(lactose);
                    }

                    productNodeRepository.save(node);
                    log.info("Seeded ProductNode: {}", product.getName());
                }
            }
            
            log.info("Neo4j Seeding Completed!");
        } catch (Exception e) {
            log.warn("Neo4j synchronization failed during startup (Neo4j may not be running): {}", e.getMessage());
            // We don't rethrow to allow the application to start even without Neo4j
        }
    }
}
