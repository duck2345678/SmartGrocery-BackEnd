package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.UnitAlias;
import com.smartgrocery.backend.entity.UnitCanonical;
import com.smartgrocery.backend.repository.jpa.UnitAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuantityParsingService {

    private static final Pattern SIMPLE_PATTERN =
            Pattern.compile("(?i)^\\s*(\\d+(?:[\\.,]\\d+)?|1/2|1/3|1/4|nua|v[aà]i)\\s+(.+?)\\s*$");

    private final UnitAliasRepository unitAliasRepository;
    private final IngredientTextNormalizer normalizer;
    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;

    public ParsedQuantity parse(String rawQuantity) {
        if (rawQuantity == null || rawQuantity.isBlank()) {
            return ParsedQuantity.unparsed(rawQuantity);
        }

        ParsedQuantity parsedByRule = parseByRule(rawQuantity);
        if (parsedByRule.status().equals(ParseStatus.PARSED) || parsedByRule.status().equals(ParseStatus.APPROX)) {
            return parsedByRule;
        }

        try {
            ParsedQuantity parsedByLlm = parseByLlm(rawQuantity);
            if (parsedByLlm.status() != ParseStatus.UNPARSED) {
                return parsedByLlm;
            }
        } catch (Exception e) {
            log.debug("LLM quantity parse fallback failed for '{}': {}", rawQuantity, e.getMessage());
        }
        return ParsedQuantity.failed(rawQuantity, "Could not parse quantity");
    }

    private ParsedQuantity parseByRule(String rawQuantity) {
        String normalized = normalizer.normalize(rawQuantity);
        Matcher matcher = SIMPLE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return ParsedQuantity.unparsed(rawQuantity);
        }
        BigDecimal value = parseValueToken(matcher.group(1));
        String unitRaw = matcher.group(2).trim();
        if (value == null || unitRaw.isBlank()) {
            return ParsedQuantity.failed(rawQuantity, "invalid value/unit");
        }

        Optional<UnitAlias> unitAlias = unitAliasRepository
                .findFirstByAliasTextNormAndLocaleAndActiveTrue(unitRaw, "vi");
        if (unitAlias.isEmpty()) {
            return ParsedQuantity.review(rawQuantity, value, unitRaw, null, new BigDecimal("0.45"), "unit alias missing");
        }

        UnitCanonical canonical = unitAlias.get().getUnitCanonical();
        ParseStatus status = Boolean.TRUE.equals(canonical.getApproximate()) ? ParseStatus.APPROX : ParseStatus.PARSED;
        BigDecimal confidence = status == ParseStatus.PARSED ? new BigDecimal("0.90") : new BigDecimal("0.70");
        return ParsedQuantity.of(rawQuantity, value, unitRaw, canonical, confidence, status, "rule_parse");
    }

    private ParsedQuantity parseByLlm(String rawQuantity) throws Exception {
        String prompt = """
                Parse this Vietnamese quantity string into JSON with keys:
                value (number), unit_raw (string), confidence (0..1), status (PARSED|APPROX|FAILED|REVIEW).
                Return JSON only.
                """;
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", rawQuantity));
        OpenRouterClient.AiCompletionResult result = openRouterClient
                .chatCompletion(prompt, messages, null, java.time.Duration.ofSeconds(5))
                .blockOptional()
                .orElse(null);
        if (result == null || result.getReply() == null || result.getReply().isBlank()) {
            return ParsedQuantity.unparsed(rawQuantity);
        }
        JsonNode root = objectMapper.readTree(result.getReply());
        BigDecimal value = root.hasNonNull("value")
                ? BigDecimal.valueOf(root.path("value").asDouble())
                : null;
        String unitRaw = root.path("unit_raw").asText("").trim();
        String statusRaw = root.path("status").asText("FAILED").trim().toUpperCase(Locale.ROOT);
        BigDecimal confidence = BigDecimal.valueOf(root.path("confidence").asDouble(0.4))
                .setScale(4, RoundingMode.HALF_UP);
        ParseStatus status = safeStatus(statusRaw);

        if (value == null || unitRaw.isBlank()) {
            return ParsedQuantity.failed(rawQuantity, "llm missing value/unit");
        }

        String unitNorm = normalizer.normalize(unitRaw);
        Optional<UnitAlias> unitAlias = unitAliasRepository
                .findFirstByAliasTextNormAndLocaleAndActiveTrue(unitNorm, "vi");
        UnitCanonical canonical = unitAlias.map(UnitAlias::getUnitCanonical).orElse(null);
        if (canonical == null) {
            return ParsedQuantity.review(rawQuantity, value, unitRaw, null, confidence.min(new BigDecimal("0.60")), "llm_unit_unmapped");
        }

        if (status == ParseStatus.FAILED || status == ParseStatus.UNPARSED) {
            status = Boolean.TRUE.equals(canonical.getApproximate()) ? ParseStatus.APPROX : ParseStatus.PARSED;
        }
        return ParsedQuantity.of(rawQuantity, value, unitRaw, canonical, confidence, status, "llm_parse");
    }

    private ParseStatus safeStatus(String raw) {
        try {
            return ParseStatus.valueOf(raw);
        } catch (Exception e) {
            return ParseStatus.FAILED;
        }
    }

    private BigDecimal parseValueToken(String token) {
        if (token == null) {
            return null;
        }
        String t = token.trim().toLowerCase(Locale.ROOT);
        if (t.equals("nua")) return new BigDecimal("0.5");
        if (t.equals("vai") || t.equals("vài")) return new BigDecimal("3");
        if (t.equals("1/2")) return new BigDecimal("0.5");
        if (t.equals("1/3")) return new BigDecimal("0.3333");
        if (t.equals("1/4")) return new BigDecimal("0.25");
        try {
            return new BigDecimal(t.replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }

    public enum ParseStatus {
        PARSED, APPROX, FAILED, REVIEW, UNPARSED
    }

    public record ParsedQuantity(
            String raw,
            BigDecimal value,
            String unitRaw,
            UnitCanonical unitCanonical,
            BigDecimal confidence,
            ParseStatus status,
            String reason
    ) {
        public static ParsedQuantity of(
                String raw,
                BigDecimal value,
                String unitRaw,
                UnitCanonical unitCanonical,
                BigDecimal confidence,
                ParseStatus status,
                String reason
        ) {
            return new ParsedQuantity(raw, value, unitRaw, unitCanonical, confidence, status, reason);
        }

        public static ParsedQuantity unparsed(String raw) {
            return new ParsedQuantity(raw, null, null, null, BigDecimal.ZERO, ParseStatus.UNPARSED, "unparsed");
        }

        public static ParsedQuantity failed(String raw, String reason) {
            return new ParsedQuantity(raw, null, null, null, BigDecimal.ZERO, ParseStatus.FAILED, reason);
        }

        public static ParsedQuantity review(
                String raw,
                BigDecimal value,
                String unitRaw,
                UnitCanonical unitCanonical,
                BigDecimal confidence,
                String reason
        ) {
            return new ParsedQuantity(raw, value, unitRaw, unitCanonical, confidence, ParseStatus.REVIEW, reason);
        }
    }
}
