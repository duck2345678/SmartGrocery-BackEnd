package com.smartgrocery.backend.config;

import com.smartgrocery.backend.service.SeedService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner initDatabase(SeedService seedService) {
        return args -> {
            seedService.seedData();
        };
    }
}
