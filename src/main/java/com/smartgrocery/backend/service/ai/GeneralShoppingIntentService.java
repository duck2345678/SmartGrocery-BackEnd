package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.dto.ChatResponseDto;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.ShoppingScenarioAlias;
import com.smartgrocery.backend.entity.ShoppingScenario;
import com.smartgrocery.backend.entity.ShoppingScenarioItem;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.ShoppingScenarioAliasRepository;
import com.smartgrocery.backend.repository.jpa.ShoppingScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneralShoppingIntentService {

    private static final int VARIANTS_PER_SCENARIO_ITEM = 3;
    private static final int MAX_SCENARIO_VARIANTS = 12;

    private final ShoppingScenarioRepository shoppingScenarioRepository;
    private final ShoppingScenarioAliasRepository shoppingScenarioAliasRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;
    private final ShoppingItemBuilder shoppingItemBuilder;

    public record GeneralShoppingResult(String reply, List<ChatResponseDto.ShoppingItem> shoppingItems) {
        public static GeneralShoppingResult none() {
            return new GeneralShoppingResult(null, null);
        }
    }

    public record GeneralShoppingExtraction(
            String intent,
            String scenario,
            Integer people,
            Integer budget,
            List<String> keywords
    ) {
    }

    public GeneralShoppingResult detectGeneralShoppingIntent(String userMessage) {
        Optional<GeneralShoppingExtraction> aliasExtraction = extractShoppingScenarioFromAliases(userMessage);
        if (aliasExtraction.isPresent()) {
            return buildResultFromExtraction(aliasExtraction.get());
        }

        Optional<GeneralShoppingExtraction> extraction = extractShoppingIntentWithAi(userMessage);
        if (extraction.isEmpty()) {
            return GeneralShoppingResult.none();
        }

        return buildResultFromExtraction(extraction.get());
    }

    private Optional<GeneralShoppingExtraction> extractShoppingScenarioFromAliases(String userMessage) {
        String normalizedMessage = " " + normalizeVietnamese(userMessage) + " ";
        if (!hasText(normalizedMessage)) {
            return Optional.empty();
        }
        for (ShoppingScenarioAlias alias : shoppingScenarioAliasRepository.findActiveAliases()) {
            String normalizedAlias = " " + alias.getNormalizedAlias() + " ";
            if (normalizedMessage.contains(normalizedAlias) && alias.getScenario() != null) {
                return Optional.of(new GeneralShoppingExtraction(
                        "shopping_scenario",
                        alias.getScenario().getCode(),
                        null,
                        null,
                        List.of()
                ));
            }
        }
        return Optional.empty();
    }

    private GeneralShoppingResult buildResultFromExtraction(GeneralShoppingExtraction value) {
        if (!"shopping_scenario".equals(value.intent()) && !"general_shopping".equals(value.intent())) {
            return GeneralShoppingResult.none();
        }

        if (hasText(value.scenario())) {
            Optional<ShoppingScenario> scenario = shoppingScenarioRepository.findActiveByCodeWithItems(normalizeScenarioCode(value.scenario()));
            if (scenario.isPresent()) {
                List<ProductVariant> variants = findVariantsForScenario(scenario.get());
                List<ChatResponseDto.ShoppingItem> items = shoppingItemBuilder.buildShoppingItemsFromVariants(variants);
                if (items != null && !items.isEmpty()) {
                    return new GeneralShoppingResult(
                            String.format("Mình đã chuẩn bị vài gợi ý mua sắm cho %s. Bạn xem danh sách bên dưới nhé.", scenario.get().getName()),
                            items
                    );
                }
            }
            log.info("[GeneralShopping] Scenario '{}' not found or empty, falling back to extracted keywords.", value.scenario());
        }

        List<ProductVariant> fallbackVariants = findVariantsByKeywords(value.keywords());
        if (fallbackVariants.isEmpty()) {
            return GeneralShoppingResult.none();
        }
        return new GeneralShoppingResult(
                "Mình đã tìm được một vài sản phẩm phù hợp với nhu cầu của bạn. Bạn xem danh sách bên dưới nhé.",
                shoppingItemBuilder.buildShoppingItemsFromVariants(fallbackVariants)
        );
    }

    public Optional<GeneralShoppingExtraction> parseShoppingIntentExtraction(String reply) {
        if (!hasText(reply)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(reply);
            String intent = root.path("intent").asText("");
            if (!"shopping_scenario".equals(intent) && !"general_shopping".equals(intent)) {
                return Optional.empty();
            }

            String scenario = root.path("scenario").isMissingNode() || root.path("scenario").isNull()
                    ? null
                    : root.path("scenario").asText(null);
            Integer people = readNullableInt(root.path("people"));
            Integer budget = readNullableInt(root.path("budget"));
            List<String> keywords = new ArrayList<>();
            JsonNode keywordsNode = root.path("keywords");
            if (keywordsNode.isArray()) {
                keywordsNode.forEach(node -> {
                    if (hasText(node.asText(null))) {
                        keywords.add(node.asText().trim());
                    }
                });
            }
            JsonNode itemsNode = root.path("items");
            if (itemsNode.isArray()) {
                itemsNode.forEach(node -> {
                    String keyword = node.path("keyword").asText(null);
                    if (hasText(keyword)) {
                        keywords.add(keyword.trim());
                    }
                });
            }
            return Optional.of(new GeneralShoppingExtraction(intent, scenario, people, budget, keywords));
        } catch (Exception e) {
            log.debug("[GeneralShopping] Could not parse AI extraction JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<GeneralShoppingExtraction> extractShoppingIntentWithAi(String userMessage) {
        if (!hasText(userMessage) || openRouterClient == null || objectMapper == null) {
            return Optional.empty();
        }
        String systemPrompt = """
                You extract general shopping intent from Vietnamese user messages.
                Return only JSON:
                {"intent":"shopping_scenario"|"general_shopping"|"other","scenario":string|null,"people":number|null,"budget":number|null,"keywords":[string]}
                Rules:
                - Use shopping_scenario when the user asks for a known shopping situation such as picnic, breakfast, baby care, cleaning, office snack.
                - scenario must be an uppercase stable code when clear: PICNIC, BREAKFAST, BABY_CARE, CLEANING, OFFICE_SNACK, HOT_POT, BIRTHDAY_PARTY.
                - Extract constraints like people and budget when present.
                - keywords are fallback product/category phrases only when scenario is unknown or extra details are useful.
                - Do not invent actual product prices or promotions.
                """;
        try {
            OpenRouterClient.AiCompletionResult result = openRouterClient
                    .chatCompletion(systemPrompt, List.of(Map.of("role", "user", "content", userMessage)), null, Duration.ofSeconds(5))
                    .block();
            if (result == null || !result.isSuccess()) {
                return Optional.empty();
            }
            return parseShoppingIntentExtraction(result.getReply());
        } catch (Exception e) {
            log.debug("[GeneralShopping] AI extraction fallback to no-op: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private List<ProductVariant> findVariantsForScenario(ShoppingScenario scenario) {
        Map<Long, ProductVariant> deduped = new LinkedHashMap<>();
        List<ShoppingScenarioItem> items = scenario.getItems() == null ? List.of() : scenario.getItems();
        for (ShoppingScenarioItem item : items) {
            List<ProductVariant> variants = switch (item.getEntityType()) {
                case "CATEGORY" -> productVariantRepository.findActiveByCategoryCode(item.getEntityValue(), VARIANTS_PER_SCENARIO_ITEM);
                case "KEYWORD" -> productVariantRepository.findTop10ActiveByKeyword(item.getEntityValue()).stream()
                        .limit(VARIANTS_PER_SCENARIO_ITEM)
                        .toList();
                default -> List.of();
            };
            for (ProductVariant variant : variants) {
                if (variant != null && variant.getProduct() != null) {
                    deduped.putIfAbsent(variant.getProduct().getId(), variant);
                }
                if (deduped.size() >= MAX_SCENARIO_VARIANTS) {
                    return new ArrayList<>(deduped.values());
                }
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private List<ProductVariant> findVariantsByKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductVariant> deduped = new LinkedHashMap<>();
        for (String keyword : keywords) {
            if (!hasText(keyword)) {
                continue;
            }
            List<ProductVariant> variants = productVariantRepository.findTop10ActiveByKeyword(keyword);
            if (variants.isEmpty()) {
                variants = productVariantRepository.searchActiveForSubstitution(normalizeVietnamese(keyword));
            }
            for (ProductVariant variant : variants) {
                if (variant != null && variant.getProduct() != null) {
                    deduped.putIfAbsent(variant.getProduct().getId(), variant);
                }
                if (deduped.size() >= MAX_SCENARIO_VARIANTS) {
                    return new ArrayList<>(deduped.values());
                }
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private String normalizeScenarioCode(String scenario) {
        return scenario == null ? "" : scenario.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private Integer readNullableInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.canConvertToInt() ? node.asInt() : null;
    }

    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd').replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
