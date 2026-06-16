package com.smartgrocery.backend.service.recommendation;

import com.smartgrocery.backend.config.AllergyProperties;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AllergyRules {
    private final Map<String, List<String>> normalizedAliases;

    public AllergyRules(AllergyProperties allergyProperties) {
        this.normalizedAliases = normalizeAliasMap(allergyProperties.getAliases());
    }

    public Set<String> extractTokens(String allergies) {
        if (allergies == null || allergies.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : allergies.split("[,;/]")) {
            String normalized = normalize(token);
            if (!normalized.isBlank()) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    public boolean matchesAny(String text, Set<String> allergyTokens) {
        if (text == null || text.isBlank() || allergyTokens == null || allergyTokens.isEmpty()) {
            return false;
        }

        String normalizedText = normalize(text);
        Set<String> expandedTokens = expandTokens(allergyTokens);
        return expandedTokens.stream().anyMatch(normalizedText::contains);
    }

    public String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Set<String> expandTokens(Set<String> allergyTokens) {
        Set<String> expanded = new LinkedHashSet<>();
        for (String token : allergyTokens) {
            String normalizedToken = normalize(token);
            if (normalizedToken.isBlank()) {
                continue;
            }
            expanded.add(normalizedToken);
            expanded.addAll(resolveAliases(normalizedToken));
        }
        return expanded;
    }

    private List<String> resolveAliases(String token) {
        if (token.isBlank()) {
            return List.of();
        }

        Set<String> aliases = new LinkedHashSet<>();
        normalizedAliases.forEach((group, configuredAliases) -> {
            if (group.equals(token) || configuredAliases.contains(token)) {
                aliases.add(group);
                aliases.addAll(configuredAliases);
            }
        });
        return List.copyOf(aliases);
    }

    private Map<String, List<String>> normalizeAliasMap(Map<String, List<String>> aliases) {
        Map<String, List<String>> normalized = new java.util.LinkedHashMap<>();
        if (aliases == null) {
            return normalized;
        }

        aliases.forEach((group, values) -> {
            String normalizedGroup = normalize(group);
            if (normalizedGroup.isBlank()) {
                return;
            }
            Set<String> merged = new LinkedHashSet<>();
            merged.add(normalizedGroup);
            if (values != null) {
                for (String value : values) {
                    String normalizedValue = normalize(value);
                    if (!normalizedValue.isBlank()) {
                        merged.add(normalizedValue);
                    }
                }
            }
            normalized.put(normalizedGroup, List.copyOf(merged));
        });
        return normalized;
    }
}
