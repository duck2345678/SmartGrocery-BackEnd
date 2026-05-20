package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.SystemConfig;
import com.smartgrocery.backend.repository.jpa.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiMatchingV2FlagService {

    private static final String KEY_ENABLED = "ai.matching.v2.enabled";
    private static final String KEY_PERCENT = "ai.matching.v2.canary.percent";
    private static final String KEY_ALLOWLIST = "ai.matching.v2.canary.allowlist";

    private final SystemConfigRepository systemConfigRepository;

    @Value("${ai.matching.v2.enabled:false}")
    private boolean defaultEnabled;

    @Value("${ai.matching.v2.canary.percent:0}")
    private int defaultCanaryPercent;

    @Value("${ai.matching.v2.canary.allowlist:}")
    private String defaultAllowlist;

    public boolean isEnabledForUser(Long userId) {
        if (!isEnabled()) {
            return false;
        }
        if (userId == null || userId <= 0) {
            return false;
        }
        Set<Long> allowlist = parseAllowlist(readConfig(KEY_ALLOWLIST).orElse(defaultAllowlist));
        if (allowlist.contains(userId)) {
            return true;
        }
        int percent = clampPercent(parseInt(readConfig(KEY_PERCENT).orElse(String.valueOf(defaultCanaryPercent))));
        int bucket = Math.floorMod(userId.hashCode(), 100);
        return bucket < percent;
    }

    public boolean isEnabled() {
        return parseBoolean(readConfig(KEY_ENABLED).orElse(String.valueOf(defaultEnabled)));
    }

    private Optional<String> readConfig(String key) {
        return systemConfigRepository.findById(key)
                .filter(SystemConfig::getActive)
                .map(SystemConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank());
    }

    private Set<Long> parseAllowlist(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(token -> {
                    try {
                        long id = Long.parseLong(token);
                        if (id > 0) {
                            ids.add(id);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                });
        return ids;
    }

    private boolean parseBoolean(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(v) || "true".equals(v) || "yes".equals(v) || "on".equals(v);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultCanaryPercent;
        }
    }

    private int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    public String debugConfigSnapshot() {
        return Arrays.asList(KEY_ENABLED, KEY_PERCENT, KEY_ALLOWLIST).stream()
                .map(key -> key + "=" + readConfig(key).orElse("<default>"))
                .collect(Collectors.joining(", "));
    }
}
