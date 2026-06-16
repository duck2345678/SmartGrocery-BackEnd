package com.smartgrocery.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app.allergy")
public class AllergyProperties {
    private Map<String, List<String>> aliases = new LinkedHashMap<>();

    public Map<String, List<String>> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, List<String>> aliases) {
        this.aliases = aliases;
    }
}
