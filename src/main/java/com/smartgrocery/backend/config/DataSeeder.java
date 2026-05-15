package com.smartgrocery.backend.config;

import com.smartgrocery.backend.service.SeedService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    @Bean
    @ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = true)
    CommandLineRunner initDatabase(SeedService seedService) {
        return args -> {
            seedService.migrateSchema();
            seedService.seedData();
        };
    }
}
