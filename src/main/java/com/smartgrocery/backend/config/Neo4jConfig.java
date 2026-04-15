package com.smartgrocery.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJpaRepositories(basePackages = "com.smartgrocery.backend.repository")
@EnableNeo4jRepositories(
        basePackages = "com.smartgrocery.backend.repository.graph",
        transactionManagerRef = "neo4jTransactionManager"
)
public class Neo4jConfig {

    @Bean
    public Neo4jTransactionManager neo4jTransactionManager(
            Driver driver,
            DatabaseSelectionProvider databaseSelectionProvider
    ) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
