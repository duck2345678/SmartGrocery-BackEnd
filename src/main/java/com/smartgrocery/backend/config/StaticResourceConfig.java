package com.smartgrocery.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.products-dir}")
    private String productsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(productsDir).toAbsolutePath().normalize();
        String location = "file:" + uploadPath.toString() + "/";
        registry.addResourceHandler("/uploads/products/**").addResourceLocations(location);
    }
}

