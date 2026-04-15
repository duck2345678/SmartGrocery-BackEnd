package com.smartgrocery.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartGroceryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartGroceryApplication.class, args);
    }

}
