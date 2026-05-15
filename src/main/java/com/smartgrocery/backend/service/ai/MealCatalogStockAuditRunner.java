package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meal.catalog.audit.enabled", havingValue = "true")
public class MealCatalogStockAuditRunner implements ApplicationRunner {

    private final MealCatalogStockAuditService auditService;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext applicationContext;

    @Value("${meal.catalog.audit.output:scratch/meal-catalog-stock-audit.json}")
    private String outputPath;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        MealCatalogStockAuditService.MealCatalogAuditReport report =
                auditService.auditCatalogAgainstCurrentStock();
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        Path path = Path.of(outputPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, json, StandardCharsets.UTF_8);
        log.info("Meal catalog stock audit written to {}", path.toAbsolutePath());
        log.info("Meal catalog stock audit summary: totalMeals={}, cookable={}, partial={}, notCookable={}, cookableRate={}",
                report.totalMeals(),
                report.cookableMeals(),
                report.partiallyCookableMeals(),
                report.notCookableMeals(),
                report.cookableRate());
        applicationContext.close();
    }
}
