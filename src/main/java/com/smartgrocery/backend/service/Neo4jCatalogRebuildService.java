package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.InventoryStock;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jCatalogRebuildService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DELETED = "DELETED";

    private final Neo4jClient neo4jClient;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryStockRepository inventoryStockRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> auditCatalogGraph() {
        List<Product> sqlProducts = productRepository.findAll();
        Set<Long> activeSqlProductIds = sqlProducts.stream()
                .filter(this::isActiveProduct)
                .map(Product::getId)
                .collect(Collectors.toSet());

        Set<Long> graphProductIds = fetchGraphProductIds();
        Set<Long> staleGraphIds = new TreeSet<>(graphProductIds);
        staleGraphIds.removeAll(activeSqlProductIds);

        Set<Long> missingGraphIds = new TreeSet<>(activeSqlProductIds);
        missingGraphIds.removeAll(graphProductIds);

        Map<String, Object> graphCounts = Map.of(
                "products", graphCount("Product"),
                "variants", graphCount("ProductVariant"),
                "stocks", graphCount("InventoryStock"),
                "categories", graphCount("Category"),
                "ingredients", graphCount("Ingredient"),
                "synonyms", graphCount("Synonym"),
                "semanticCache", graphCount("SemanticCache")
        );

        return Map.of(
                "sqlProductsTotal", sqlProducts.size(),
                "sqlProductsActive", activeSqlProductIds.size(),
                "graphCounts", graphCounts,
                "staleGraphProductIds", staleGraphIds.stream().limit(50).toList(),
                "staleGraphProductCount", staleGraphIds.size(),
                "missingGraphProductIds", missingGraphIds.stream().limit(50).toList(),
                "missingGraphProductCount", missingGraphIds.size(),
                "descriptionIssues", sqlProducts.stream()
                        .filter(this::isActiveProduct)
                        .filter(p -> isBlank(p.getDescription()) || isBlank(p.getShortDescription()))
                        .map(p -> Map.of("productId", p.getId(), "name", nullToEmpty(p.getName())))
                        .limit(50)
                        .toList()
        );
    }

    public Map<String, Object> rebuildCatalogGraph() {
        log.info("Starting full Neo4j catalog rebuild from SQL catalog");
        List<Product> products = productRepository.findActiveWithCategory();

        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, List<ProductVariant>> variantsByProduct = productIds.isEmpty()
                ? Map.of()
                : productVariantRepository.findByProductIdsAndStatusWithProduct(productIds, ACTIVE).stream()
                .filter(v -> v.getProduct() != null && productIds.contains(v.getProduct().getId()))
                .collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        List<ProductVariant> activeVariants = variantsByProduct.values().stream().flatMap(List::stream).toList();
        Map<Long, Long> stockByVariant = buildStockMap(activeVariants);

        GraphPayload payload = buildPayload(products, variantsByProduct, stockByVariant);

        ensureSchema();
        resetCatalogGraph();
        importCategories(payload.categories());
        importProducts(payload.products());
        importVariants(payload.variants());
        importIngredients(payload.ingredients());
        importSynonyms(payload.synonyms());
        importNutritionRules(payload.conditions(), payload.preferences(), payload.goals());
        createSimilarityEdges();

        Map<String, Object> audit = auditCatalogGraph();
        log.info("Neo4j catalog rebuild completed: {}", audit);
        return Map.of(
                "message", "Neo4j catalog graph rebuilt from current SQL products",
                "rebuiltAt", LocalDateTime.now().toString(),
                "importedProducts", payload.products().size(),
                "importedVariants", payload.variants().size(),
                "importedIngredients", payload.ingredients().size(),
                "importedSynonyms", payload.synonyms().size(),
                "audit", audit
        );
    }

    private GraphPayload buildPayload(
            List<Product> products,
            Map<Long, List<ProductVariant>> variantsByProduct,
            Map<Long, Long> stockByVariant
    ) {
        List<Map<String, Object>> categories = new ArrayList<>();
        List<Map<String, Object>> productRows = new ArrayList<>();
        List<Map<String, Object>> variantRows = new ArrayList<>();
        List<Map<String, Object>> ingredientRows = new ArrayList<>();
        List<Map<String, Object>> synonymRows = new ArrayList<>();
        List<Map<String, Object>> conditionRows = new ArrayList<>();
        List<Map<String, Object>> preferenceRows = new ArrayList<>();
        List<Map<String, Object>> goalRows = new ArrayList<>();
        Set<String> seenCategories = new HashSet<>();
        Set<String> seenIngredients = new HashSet<>();
        Set<String> seenSynonyms = new HashSet<>();
        Set<String> seenConditions = new HashSet<>();
        Set<String> seenPreferences = new HashSet<>();
        Set<String> seenGoals = new HashSet<>();

        for (Product product : products) {
            List<ProductVariant> variants = variantsByProduct.getOrDefault(product.getId(), List.of());
            int stock = variants.stream()
                    .mapToInt(v -> stockByVariant.getOrDefault(v.getId(), 0L).intValue())
                    .sum();
            if (stock <= 0 || variants.isEmpty()) {
                continue;
            }

            String categoryName = product.getCategory() != null ? product.getCategory().getName() : "Uncategorized";
            String categoryCode = product.getCategory() != null ? product.getCategory().getCategoryCode() : "UNCATEGORIZED";
            if (seenCategories.add(categoryName)) {
                categories.add(Map.of(
                        "name", categoryName,
                        "categoryCode", categoryCode,
                        "description", product.getCategory() != null ? nullToEmpty(product.getCategory().getDescription()) : ""
                ));
            }

            ProductVariant primary = variants.get(0);
            double price = primary.getNetPrice() != null ? primary.getNetPrice().doubleValue() : 0.0;
            String variantSkus = variants.stream().map(ProductVariant::getSku).filter(Objects::nonNull).collect(Collectors.joining(", "));
            String variantNames = variants.stream().map(v -> nullToEmpty(v.getVariantName())).filter(s -> !s.isBlank()).collect(Collectors.joining(", "));
            String familyTags = String.join(",", inferFamilyTags(product, variants));
            String searchText = buildSearchText(product, variants, categoryName, familyTags);

            productRows.add(Map.of(
                    "productId", product.getId(),
                    "props", mapOfEntries(
                            "productId", product.getId(),
                            "name", nullToEmpty(product.getName()),
                            "productCode", nullToEmpty(product.getProductCode()),
                            "status", product.getStatus(),
                            "price", price,
                            "unit", nullToEmpty(primary.getUnit()),
                            "description", nullToEmpty(product.getDescription()),
                            "shortDescription", nullToEmpty(product.getShortDescription()),
                            "categoryName", categoryName,
                            "originCountry", nullToEmpty(product.getOriginCountry()),
                            "variantSkus", variantSkus,
                            "variantNames", variantNames,
                            "familyTags", familyTags,
                            "searchText", searchText,
                            "stock", stock,
                            "hasStock", true,
                            "isStaple", Boolean.TRUE.equals(product.getIsStaple()),
                            "source", "sql-products",
                            "updatedAt", product.getUpdatedAt() != null ? product.getUpdatedAt().toString() : LocalDateTime.now().toString()
                    ),
                    "categoryName", categoryName
            ));

            for (ProductVariant variant : variants) {
                long variantStock = stockByVariant.getOrDefault(variant.getId(), 0L);
                variantRows.add(Map.of(
                        "productId", product.getId(),
                        "variantId", variant.getId(),
                        "stockKey", "variant-" + variant.getId(),
                        "props", mapOfEntries(
                                "variantId", variant.getId(),
                                "sku", nullToEmpty(variant.getSku()),
                                "barcode", nullToEmpty(variant.getBarcode()),
                                "variantName", nullToEmpty(variant.getVariantName()),
                                "unit", nullToEmpty(variant.getUnit()),
                                "packageSize", nullToEmpty(variant.getPackageSize()),
                                "color", nullToEmpty(variant.getColor()),
                                "size", nullToEmpty(variant.getSize()),
                                "netPrice", decimalToDouble(variant.getNetPrice()),
                                "status", nullToEmpty(variant.getStatus()),
                                "stock", variantStock
                        ),
                        "stockProps", mapOfEntries(
                                "stockKey", "variant-" + variant.getId(),
                                "variantId", variant.getId(),
                                "availableQuantity", variantStock,
                                "reservedQuantity", 0
                        )
                ));
            }

            for (String ingredient : inferIngredients(product, variants)) {
                String key = product.getId() + "::" + ingredient;
                if (seenIngredients.add(key)) {
                    ingredientRows.add(Map.of("productId", product.getId(), "name", ingredient));
                }
            }

            for (String synonym : inferSynonyms(product, variants, categoryName)) {
                String key = product.getId() + "::" + synonym;
                if (seenSynonyms.add(key)) {
                    synonymRows.add(Map.of("productId", product.getId(), "name", synonym));
                }
            }

            for (String condition : inferAvoidConditions(product, categoryName)) {
                String key = product.getId() + "::" + condition;
                if (seenConditions.add(key)) {
                    conditionRows.add(Map.of("productId", product.getId(), "name", condition));
                }
            }

            for (String preference : inferPreferences(product, categoryName)) {
                String key = product.getId() + "::" + preference;
                if (seenPreferences.add(key)) {
                    preferenceRows.add(Map.of("productId", product.getId(), "name", preference));
                }
            }

            for (String goal : inferGoals(product, variants)) {
                String key = product.getId() + "::" + goal;
                if (seenGoals.add(key)) {
                    goalRows.add(Map.of("productId", product.getId(), "name", goal));
                }
            }
        }

        return new GraphPayload(categories, productRows, variantRows, ingredientRows, synonymRows, conditionRows, preferenceRows, goalRows);
    }

    private void resetCatalogGraph() {
        neo4jClient.query("""
                MATCH (n)
                WHERE n:Product OR n:ProductVariant OR n:InventoryStock OR n:Category
                   OR n:Ingredient OR n:Synonym OR n:SemanticCache
                DETACH DELETE n
                """).run();
    }

    private void ensureSchema() {
        List<String> statements = List.of(
                "CREATE CONSTRAINT product_product_id_unique IF NOT EXISTS FOR (p:Product) REQUIRE p.productId IS UNIQUE",
                "CREATE CONSTRAINT category_name_unique IF NOT EXISTS FOR (c:Category) REQUIRE c.name IS UNIQUE",
                "CREATE CONSTRAINT ingredient_name_unique IF NOT EXISTS FOR (i:Ingredient) REQUIRE i.name IS UNIQUE",
                "CREATE CONSTRAINT synonym_name_unique IF NOT EXISTS FOR (s:Synonym) REQUIRE s.name IS UNIQUE",
                "CREATE CONSTRAINT variant_id_unique IF NOT EXISTS FOR (v:ProductVariant) REQUIRE v.variantId IS UNIQUE",
                "CREATE CONSTRAINT stock_key_unique IF NOT EXISTS FOR (s:InventoryStock) REQUIRE s.stockKey IS UNIQUE",
                "DROP INDEX productFullTextIndex IF EXISTS",
                "CREATE FULLTEXT INDEX productFullTextIndex IF NOT EXISTS FOR (p:Product) ON EACH [p.name, p.description, p.shortDescription, p.categoryName, p.productCode, p.variantSkus, p.variantNames, p.familyTags, p.searchText]",
                "CREATE INDEX product_status_idx IF NOT EXISTS FOR (p:Product) ON (p.status)",
                "CREATE INDEX product_stock_idx IF NOT EXISTS FOR (p:Product) ON (p.hasStock)"
        );
        statements.forEach(statement -> {
            try {
                neo4jClient.query(statement).run();
            } catch (Exception e) {
                log.warn("Neo4j schema statement failed: {} -> {}", statement, e.getMessage());
            }
        });
    }

    private void importCategories(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        neo4jClient.query("""
                UNWIND $rows AS row
                MERGE (c:Category {name: row.name})
                SET c.categoryCode = row.categoryCode,
                    c.description = row.description
                """).bind(rows).to("rows").run();
    }

    private void importProducts(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        neo4jClient.query("""
                UNWIND $rows AS row
                MERGE (p:Product {productId: row.productId})
                SET p += row.props
                WITH p, row
                MATCH (c:Category {name: row.categoryName})
                MERGE (p)-[:BELONGS_TO]->(c)
                """).bind(rows).to("rows").run();
    }

    private void importVariants(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        neo4jClient.query("""
                UNWIND $rows AS row
                MATCH (p:Product {productId: row.productId})
                MERGE (v:ProductVariant {variantId: row.variantId})
                SET v += row.props
                MERGE (p)-[:HAS_VARIANT]->(v)
                MERGE (s:InventoryStock {stockKey: row.stockKey})
                SET s += row.stockProps
                MERGE (v)-[:HAS_STOCK]->(s)
                """).bind(rows).to("rows").run();
    }

    private void importIngredients(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        neo4jClient.query("""
                UNWIND $rows AS row
                MATCH (p:Product {productId: row.productId})
                MERGE (i:Ingredient {name: row.name})
                MERGE (p)-[:CONTAINS_INGREDIENT {source: 'catalog-rule'}]->(i)
                """).bind(rows).to("rows").run();
    }

    private void importSynonyms(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        neo4jClient.query("""
                UNWIND $rows AS row
                MATCH (p:Product {productId: row.productId})
                MERGE (s:Synonym {name: row.name})
                MERGE (s)-[:MAPS_TO]->(p)
                """).bind(rows).to("rows").run();
    }

    private void importNutritionRules(
            List<Map<String, Object>> conditions,
            List<Map<String, Object>> preferences,
            List<Map<String, Object>> goals
    ) {
        if (!conditions.isEmpty()) {
            neo4jClient.query("""
                    UNWIND $rows AS row
                    MATCH (p:Product {productId: row.productId})
                    MERGE (c:Condition {name: row.name})
                    MERGE (p)-[:AVOID_FOR]->(c)
                    """).bind(conditions).to("rows").run();
        }
        if (!preferences.isEmpty()) {
            neo4jClient.query("""
                    UNWIND $rows AS row
                    MATCH (p:Product {productId: row.productId})
                    MERGE (pref:DietaryPreference {name: row.name})
                    MERGE (p)-[:SUITABLE_FOR]->(pref)
                    """).bind(preferences).to("rows").run();
        }
        if (!goals.isEmpty()) {
            neo4jClient.query("""
                    UNWIND $rows AS row
                    MATCH (p:Product {productId: row.productId})
                    MERGE (goal:DietaryGoal {name: row.name})
                    MERGE (p)-[:SUITABLE_FOR_GOAL]->(goal)
                    """).bind(goals).to("rows").run();
        }
    }

    private void createSimilarityEdges() {
        neo4jClient.query("""
                MATCH (p1:Product), (p2:Product)
                WHERE p1.productId < p2.productId
                  AND p1.categoryName = p2.categoryName
                  AND p1.hasStock = true
                  AND p2.hasStock = true
                WITH p1, p2 LIMIT 3000
                MERGE (p1)-[:SIMILAR_TO {basis: 'category'}]->(p2)
                MERGE (p2)-[:SIMILAR_TO {basis: 'category'}]->(p1)
                """).run();
    }

    private Map<Long, Long> buildStockMap(List<ProductVariant> variants) {
        List<Long> variantIds = variants.stream().map(ProductVariant::getId).filter(Objects::nonNull).toList();
        if (variantIds.isEmpty()) return Map.of();
        Map<Long, Long> result = inventoryStockRepository.sumAvailableByVariantIds(variantIds).stream()
                .collect(Collectors.toMap(
                        InventoryStockRepository.VariantStockSum::getVariantId,
                        InventoryStockRepository.VariantStockSum::getTotalAvailable
                ));
        variantIds.forEach(id -> result.putIfAbsent(id, 0L));
        return result;
    }

    private List<String> inferFamilyTags(Product product, List<ProductVariant> variants) {
        String text = normalizedBlob(product, variants, product.getCategory() != null ? product.getCategory().getName() : "");
        Set<String> tags = new LinkedHashSet<>();
        if (containsAny(text, "bo", "beef", "steak")) tags.add("beef");
        if (containsAny(text, "heo", "lon", "pork", "ba roi", "nac dam", "suon")) tags.add("pork");
        if (containsAny(text, "ga", "chicken")) tags.add("chicken");
        if (containsAny(text, "tom", "cua", "ca ", "ca-", "muc", "ngheu", "oc", "hau", "bach tuoc", "seafood")) tags.add("seafood");
        if (containsAny(text, "sua", "phomai", "pho mai", "bo lat", "kem tuoi", "dairy")) tags.add("dairy");
        if (containsAny(text, "rau", "cu", "nam", "salad", "xalach", "cai", "vegetable")) tags.add("vegetable");
        if (containsAny(text, "tao", "chuoi", "cam", "nho", "xoai", "dua", "fruit")) tags.add("fruit");
        if (containsAny(text, "gao", "mi", "bun", "pho", "yen mach", "ngu coc")) tags.add("carb");
        if (containsAny(text, "dau an", "dau me", "bo", "hat", "hanh nhan", "dieu")) tags.add("fat");
        if (Boolean.TRUE.equals(product.getIsStaple())) tags.add("staple");
        if (tags.isEmpty()) tags.add("grocery");
        return List.copyOf(tags);
    }

    private List<String> inferIngredients(Product product, List<ProductVariant> variants) {
        Set<String> ingredients = new LinkedHashSet<>(inferFamilyTags(product, variants));
        String normalizedName = normalize(product.getName());
        if (!normalizedName.isBlank()) ingredients.add(normalizedName);
        return ingredients.stream().limit(8).toList();
    }

    private List<String> inferSynonyms(Product product, List<ProductVariant> variants, String categoryName) {
        Set<String> synonyms = new LinkedHashSet<>();
        String name = normalize(product.getName());
        String category = normalize(categoryName);
        if (!name.isBlank()) {
            synonyms.add(name);
            Arrays.stream(name.split("\\s+"))
                    .filter(token -> token.length() >= 3)
                    .limit(5)
                    .forEach(synonyms::add);
        }
        inferFamilyTags(product, variants).forEach(synonyms::add);
        if (!category.isBlank()) synonyms.add(category);
        return synonyms.stream().limit(12).toList();
    }

    private List<String> inferAvoidConditions(Product product, String categoryName) {
        String text = normalizedBlob(product, List.of(), categoryName);
        List<String> conditions = new ArrayList<>();
        if (containsAny(text, "tom", "cua", "muc", "ngheu", "oc", "hau", "bach tuoc", "hai san")) {
            conditions.add("Seafood Allergy");
        }
        if (containsAny(text, "sua", "phomai", "pho mai", "bo lat", "kem tuoi", "dairy")) {
            conditions.add("Lactose Intolerance");
        }
        if (containsAny(text, "hanh nhan", "dieu", "hat")) {
            conditions.add("Nut Allergy");
        }
        if (containsAny(text, "duong", "keo", "soda", "coca", "banh")) {
            conditions.add("Diabetes");
        }
        return conditions;
    }

    private List<String> inferPreferences(Product product, String categoryName) {
        String text = normalizedBlob(product, List.of(), categoryName);
        List<String> preferences = new ArrayList<>();
        boolean animal = containsAny(text, "bo", "heo", "lon", "ga", "tom", "cua", "ca ", "muc", "trung", "sua", "phomai", "pho mai");
        if (!animal) preferences.add("Vegetarian");
        if (!animal && !containsAny(text, "sua", "phomai", "pho mai", "bo lat")) preferences.add("Vegan");
        if (containsAny(text, "bo", "ga", "ca", "tom", "trung", "dau hu", "hat")) preferences.add("High Protein");
        return preferences;
    }

    private List<String> inferGoals(Product product, List<ProductVariant> variants) {
        String text = normalizedBlob(product, variants, product.getCategory() != null ? product.getCategory().getName() : "");
        List<String> goals = new ArrayList<>();
        if (containsAny(text, "rau", "cu", "trai cay", "salad", "sua chua khong duong")) goals.add("Weight Loss");
        if (containsAny(text, "bo", "ga", "ca", "tom", "trung", "dau hu", "sua", "hat")) goals.add("Muscle Gain");
        if (goals.isEmpty()) goals.add("Maintenance");
        return goals;
    }

    private String buildSearchText(Product product, List<ProductVariant> variants, String categoryName, String familyTags) {
        return String.join(" ",
                nullToEmpty(product.getProductCode()),
                nullToEmpty(product.getName()),
                nullToEmpty(product.getShortDescription()),
                nullToEmpty(product.getDescription()),
                categoryName,
                nullToEmpty(product.getOriginCountry()),
                variants.stream().map(ProductVariant::getSku).filter(Objects::nonNull).collect(Collectors.joining(" ")),
                variants.stream().map(v -> nullToEmpty(v.getVariantName())).collect(Collectors.joining(" ")),
                familyTags
        );
    }

    private Set<Long> fetchGraphProductIds() {
        try {
            return neo4jClient.query("MATCH (p:Product) RETURN p.productId AS productId")
                    .fetchAs(Long.class)
                    .mappedBy((typeSystem, record) -> record.get("productId").asLong())
                    .all()
                    .stream()
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (Exception e) {
            log.warn("Neo4j audit failed while reading product ids: {}", e.getMessage());
            return Set.of();
        }
    }

    private long graphCount(String label) {
        try {
            String safeLabel = label.replaceAll("[^A-Za-z0-9_]", "");
            return neo4jClient.query("MATCH (n:" + safeLabel + ") RETURN count(n) AS count")
                    .fetchAs(Long.class)
                    .mappedBy((typeSystem, record) -> record.get("count").asLong())
                    .one()
                    .orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    private boolean isActiveProduct(Product product) {
        return product != null
                && product.getId() != null
                && !DELETED.equalsIgnoreCase(nullToEmpty(product.getStatus()))
                && ACTIVE.equalsIgnoreCase(nullToEmpty(product.getStatus()));
    }

    private String normalizedBlob(Product product, List<ProductVariant> variants, String categoryName) {
        return normalize(String.join(" ",
                nullToEmpty(product.getName()),
                nullToEmpty(product.getShortDescription()),
                nullToEmpty(product.getDescription()),
                nullToEmpty(categoryName),
                variants.stream().map(v -> nullToEmpty(v.getVariantName())).collect(Collectors.joining(" "))
        ));
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(normalize(needle))) return true;
        }
        return false;
    }

    private String normalize(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double decimalToDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private Map<String, Object> mapOfEntries(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private record GraphPayload(
            List<Map<String, Object>> categories,
            List<Map<String, Object>> products,
            List<Map<String, Object>> variants,
            List<Map<String, Object>> ingredients,
            List<Map<String, Object>> synonyms,
            List<Map<String, Object>> conditions,
            List<Map<String, Object>> preferences,
            List<Map<String, Object>> goals
    ) {}
}
