package com.smartgrocery.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication(exclude = {
        JpaRepositoriesAutoConfiguration.class,
        Neo4jRepositoriesAutoConfiguration.class,
        Neo4jReactiveRepositoriesAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class SmartGroceryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartGroceryApplication.class, args);
    }

}
