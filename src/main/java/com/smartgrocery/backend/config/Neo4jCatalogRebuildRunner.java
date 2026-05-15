package com.smartgrocery.backend.config;

import com.smartgrocery.backend.service.Neo4jCatalogRebuildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jCatalogRebuildRunner implements ApplicationRunner {

    private final Neo4jCatalogRebuildService rebuildService;
    private final ConfigurableApplicationContext applicationContext;

    @Value("${app.neo4j.rebuild-and-exit:false}")
    private boolean rebuildAndExit;

    @Override
    public void run(ApplicationArguments args) {
        if (!rebuildAndExit) {
            return;
        }

        try {
            log.info("app.neo4j.rebuild-and-exit=true, rebuilding Neo4j catalog graph once");
            rebuildService.rebuildCatalogGraph();
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        } catch (Exception e) {
            log.error("Neo4j catalog rebuild-and-exit failed", e);
            int exitCode = SpringApplication.exit(applicationContext, () -> 1);
            System.exit(exitCode);
        }
    }
}
