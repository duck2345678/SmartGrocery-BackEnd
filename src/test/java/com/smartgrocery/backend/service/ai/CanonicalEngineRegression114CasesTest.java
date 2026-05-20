package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.entity.Category;
import com.smartgrocery.backend.entity.IngredientAlias;
import com.smartgrocery.backend.entity.IngredientCanonical;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.UnitAlias;
import com.smartgrocery.backend.entity.UnitCanonical;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import com.smartgrocery.backend.repository.jpa.IngredientAliasRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.UnitAliasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalEngineRegression114CasesTest {

    @Mock
    private IngredientAliasRepository ingredientAliasRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductNodeRepository productNodeRepository;
    @Mock
    private UnitAliasRepository unitAliasRepository;
    @Mock
    private OpenRouterClient openRouterClient;

    private IngredientTextNormalizer normalizer;
    private IngredientMatchV2Service matchService;
    private StockDimensionBridgeService bridgeService;
    private QuantityParsingService quantityParsingService;

    private final Map<String, IngredientAlias> ingredientAliasByNorm = new LinkedHashMap<>();
    private final Map<String, UnitAlias> unitAliasByNorm = new LinkedHashMap<>();
    private final Map<Long, List<ProductNode>> canonicalProductNodes = new LinkedHashMap<>();
    private List<Product> products;
    private Set<Long> stockedProductIds;

    @BeforeEach
    void setup() {
        normalizer = new IngredientTextNormalizer();
        matchService = new IngredientMatchV2Service(
                normalizer,
                ingredientAliasRepository,
                productRepository,
                productNodeRepository
        );
        bridgeService = new StockDimensionBridgeService();
        quantityParsingService = new QuantityParsingService(
                unitAliasRepository,
                normalizer,
                openRouterClient,
                new ObjectMapper()
        );

        seedIngredientDictionary();
        seedUnitDictionary();
        seedProducts();

        stockedProductIds = products.stream().map(Product::getId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        when(productRepository.findAllByIdWithCategory(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return products.stream().filter(p -> ids.contains(p.getId())).toList();
        });
        when(productNodeRepository.findCanonicalByAliasNorm(anyString(), eq("vi")))
                .thenAnswer(invocation -> {
                    String norm = invocation.getArgument(0);
                    IngredientAlias alias = ingredientAliasByNorm.get(norm);
                    if (alias == null || alias.getCanonical() == null) {
                        return Optional.empty();
                    }
                    IngredientCanonical c = alias.getCanonical();
                    return Optional.of(new ProductNodeRepository.CanonicalAliasProjection() {
                        @Override
                        public Long getCanonicalId() {
                            return c.getId();
                        }

                        @Override
                        public String getCanonicalCode() {
                            return c.getCanonicalCode();
                        }

                        @Override
                        public String getCanonicalNameVi() {
                            return c.getCanonicalNameVi();
                        }

                        @Override
                        public String getIngredientFamily() {
                            return c.getIngredientFamily();
                        }

                        @Override
                        public String getDefaultDimension() {
                            return c.getDefaultDimension();
                        }

                        @Override
                        public Double getAvgWeightG() {
                            return c.getAverageWeightPerUnitG() == null ? null : c.getAverageWeightPerUnitG().doubleValue();
                        }

                        @Override
                        public Double getAvgVolumeMl() {
                            return c.getAverageVolumePerUnitMl() == null ? null : c.getAverageVolumePerUnitMl().doubleValue();
                        }
                    });
                });
        when(productNodeRepository.findProductsByCanonicalId(anyLong()))
                .thenAnswer(invocation -> canonicalProductNodes.getOrDefault(invocation.getArgument(0), List.of()));
        when(unitAliasRepository.findFirstByAliasTextNormAndLocaleAndActiveTrue(anyString(), eq("vi")))
                .thenAnswer(invocation -> Optional.ofNullable(unitAliasByNorm.get(invocation.getArgument(0))));
    }

    @Test
    void shouldPass114RegressionScenariosAcrossDiverseErrorGroups() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        int total = 0;

        List<MatchCase> matchCases = buildMatchCases60();
        int matchPassed = 0;
        for (MatchCase c : matchCases) {
            IngredientMatchV2Service.MatchDecision d = matchService.match(c.input(), stockedProductIds);
            String productName = d.matchedProduct() == null ? "" : normalize(d.matchedProduct().getName());

            boolean pass = "MATCHED".equals(d.status())
                    && d.canonical() != null
                    && c.expectedCanonical().equals(d.canonical().getCanonicalCode())
                    && productName.contains(c.expectedProductToken())
                    && (c.forbiddenToken() == null || !productName.contains(c.forbiddenToken()));
            assertTrue(pass, "Match failed for case=" + c.caseName() + " reason=" + d.reasonCode());
            matchPassed++;
            total++;
        }
        stats.put("matchCases", matchPassed);

        List<BridgeCase> bridgeCases = buildBridgeCases30();
        int bridgePassed = 0;
        for (BridgeCase c : bridgeCases) {
            StockDimensionBridgeService.BridgeResult r = bridgeService.bridgeRequiredAmountToMass(c.ingredient());
            boolean statusOk = c.expectedStatus().equals(r.status());
            boolean reasonOk = c.expectedReason().equals(r.reasonCode());
            boolean amountOk = c.expectedMassG() == null
                    ? r.requiredMassG() == null
                    : (r.requiredMassG() != null && r.requiredMassG().compareTo(c.expectedMassG()) == 0);
            assertTrue(statusOk && reasonOk && amountOk, "Bridge failed for case=" + c.caseName());
            bridgePassed++;
            total++;
        }
        stats.put("bridgeCases", bridgePassed);

        List<ParseCase> parseCases = buildParseCases24();
        int parsePassed = 0;
        for (ParseCase c : parseCases) {
            QuantityParsingService.ParsedQuantity parsed = quantityParsingService.parse(c.input());
            boolean pass = parsed.status() == c.expectedStatus()
                    && parsed.value() != null
                    && parsed.value().compareTo(c.expectedValue()) == 0
                    && parsed.unitCanonical() != null
                    && c.expectedDimension().equalsIgnoreCase(parsed.unitCanonical().getDimension());
            assertTrue(pass, "Parse failed for case=" + c.caseName() + " reason=" + parsed.reason());
            parsePassed++;
            total++;
        }
        stats.put("parseCases", parsePassed);

        assertEquals(114, total, "Regression suite must cover exactly 114 scenarios");
        assertEquals(60, stats.get("matchCases"));
        assertEquals(30, stats.get("bridgeCases"));
        assertEquals(24, stats.get("parseCases"));
    }

    private List<MatchCase> buildMatchCases60() {
        List<MatchCase> base = List.of(
                new MatchCase("ot-01", "ớt hiểm trái", "ot_hiem", "ot hiem", "cherry"),
                new MatchCase("ot-02", "ot hiem tuoi", "ot_hiem", "ot hiem", "cherry"),
                new MatchCase("nghe-01", "bột nghệ", "nghe_bot", "bot nghe", null),
                new MatchCase("nghe-02", "nghệ bột", "nghe_bot", "bot nghe", null),
                new MatchCase("nghe-03", "turmeric powder", "nghe_bot", "bot nghe", null),
                new MatchCase("cachua-01", "cà chua", "ca_chua", "ca chua", null),
                new MatchCase("cachua-02", "tomato", "ca_chua", "tomato", null),
                new MatchCase("cachua-03", "ca chua da lat", "ca_chua", "ca chua", null),
                new MatchCase("cai-01", "cải thìa", "cai_thia", "cai thia", null),
                new MatchCase("cai-02", "rau cải xanh", "cai_thia", "rau cai", null),
                new MatchCase("cai-03", "bok choy", "cai_thia", "bok choy", null),
                new MatchCase("thit-01", "thịt bằm", "thit_bam", "thit heo xay", null),
                new MatchCase("thit-02", "thit heo xay", "thit_bam", "thit heo xay", null),
                new MatchCase("tom-01", "tôm tươi", "tom_the", "tom the", null),
                new MatchCase("tom-02", "shrimp", "tom_the", "shrimp", null),
                new MatchCase("ga-01", "đùi gà", "ga_dui", "dui ga", null),
                new MatchCase("ga-02", "thit ga", "ga_dui", "dui ga", null),
                new MatchCase("suon-01", "sườn heo", "suon_heo", "suon heo", null),
                new MatchCase("caloc-01", "ca loc", "ca_loc", "ca loc", null),
                new MatchCase("hanhla-01", "hành lá", "hanh_la", "hanh la", null)
        );

        List<MatchCase> cases = new ArrayList<>();
        String[] mealSlots = {"sang", "trua", "toi"};
        for (int i = 0; i < 3; i++) {
            for (MatchCase b : base) {
                cases.add(new MatchCase(
                        b.caseName() + "-" + mealSlots[i],
                        b.input(),
                        b.expectedCanonical(),
                        b.expectedProductToken(),
                        b.forbiddenToken()
                ));
            }
        }
        assertEquals(60, cases.size());
        return cases;
    }

    private List<BridgeCase> buildBridgeCases30() {
        IngredientCanonical caChua = canonical("ca_chua", "vegetable", "count", new BigDecimal("150"));
        IngredientCanonical khoaiTay = canonical("khoai_tay", "vegetable", "count", new BigDecimal("120"));
        IngredientCanonical hanhTay = canonical("hanh_tay", "vegetable", "count", new BigDecimal("80"));
        IngredientCanonical unknown = canonical("unknown", "other", "count", null);

        UnitCanonical piece = unit("piece", "count", BigDecimal.ONE, false);
        UnitCanonical gram = unit("g", "mass", BigDecimal.ONE, false);
        UnitCanonical kg = unit("kg", "mass", new BigDecimal("1000"), false);
        UnitCanonical ml = unit("ml", "volume", BigDecimal.ONE, false);

        List<BridgeCase> cases = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            BigDecimal qty = new BigDecimal(i);
            cases.add(new BridgeCase("bridge-cachua-" + i,
                    ingredient(caChua, piece, qty),
                    "BRIDGED",
                    qty.multiply(new BigDecimal("150")).setScale(4),
                    "DIMENSION_BRIDGED"));
        }
        for (int i = 1; i <= 8; i++) {
            BigDecimal qty = new BigDecimal(i);
            cases.add(new BridgeCase("bridge-khoaitay-" + i,
                    ingredient(khoaiTay, piece, qty),
                    "BRIDGED",
                    qty.multiply(new BigDecimal("120")).setScale(4),
                    "DIMENSION_BRIDGED"));
        }
        for (int i = 1; i <= 4; i++) {
            BigDecimal qty = new BigDecimal(i);
            cases.add(new BridgeCase("bridge-hanhtay-" + i,
                    ingredient(hanhTay, piece, qty),
                    "BRIDGED",
                    qty.multiply(new BigDecimal("80")).setScale(4),
                    "DIMENSION_BRIDGED"));
        }

        cases.add(new BridgeCase("direct-mass-gram-1",
                ingredient(caChua, gram, new BigDecimal("250")),
                "BRIDGED",
                new BigDecimal("250.0000"),
                "DIRECT_MASS"));
        cases.add(new BridgeCase("direct-mass-gram-2",
                ingredient(caChua, gram, new BigDecimal("500")),
                "BRIDGED",
                new BigDecimal("500.0000"),
                "DIRECT_MASS"));
        cases.add(new BridgeCase("direct-mass-kg-1",
                ingredient(caChua, kg, new BigDecimal("1.5")),
                "BRIDGED",
                new BigDecimal("1500.0000"),
                "DIRECT_MASS"));
        cases.add(new BridgeCase("direct-mass-kg-2",
                ingredient(caChua, kg, new BigDecimal("0.75")),
                "BRIDGED",
                new BigDecimal("750.0000"),
                "DIRECT_MASS"));

        cases.add(new BridgeCase("unresolved-no-avg-1",
                ingredient(unknown, piece, new BigDecimal("2")),
                "UNRESOLVED",
                null,
                "MISSING_AVG_WEIGHT"));
        cases.add(new BridgeCase("unresolved-no-avg-2",
                ingredient(unknown, piece, new BigDecimal("5")),
                "UNRESOLVED",
                null,
                "MISSING_AVG_WEIGHT"));
        cases.add(new BridgeCase("unresolved-volume-1",
                ingredient(caChua, ml, new BigDecimal("300")),
                "UNRESOLVED",
                null,
                "DIMENSION_NOT_SUPPORTED_volume"));
        cases.add(new BridgeCase("unresolved-volume-2",
                ingredient(khoaiTay, ml, new BigDecimal("120")),
                "UNRESOLVED",
                null,
                "DIMENSION_NOT_SUPPORTED_volume"));

        assertEquals(30, cases.size());
        return cases;
    }

    private List<ParseCase> buildParseCases24() {
        List<ParseCase> cases = List.of(
                new ParseCase("parse-01", "1 kg", new BigDecimal("1"), "mass", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-02", "2 kg", new BigDecimal("2"), "mass", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-03", "250 g", new BigDecimal("250"), "mass", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-04", "3 kg", new BigDecimal("3"), "mass", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-05", "4 g", new BigDecimal("4"), "mass", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-06", "2 quả", new BigDecimal("2"), "count", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-07", "3 trai", new BigDecimal("3"), "count", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-08", "5 cái", new BigDecimal("5"), "count", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-09", "nua quả", new BigDecimal("0.5"), "count", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-10", "vài quả", new BigDecimal("3"), "count", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-11", "1 bó", new BigDecimal("1"), "count", QuantityParsingService.ParseStatus.APPROX),
                new ParseCase("parse-12", "2 bó", new BigDecimal("2"), "count", QuantityParsingService.ParseStatus.APPROX),
                new ParseCase("parse-13", "1 nhúm", new BigDecimal("1"), "mass", QuantityParsingService.ParseStatus.APPROX),
                new ParseCase("parse-14", "nua nhum", new BigDecimal("0.5"), "mass", QuantityParsingService.ParseStatus.APPROX),
                new ParseCase("parse-15", "3 nhum", new BigDecimal("3"), "mass", QuantityParsingService.ParseStatus.APPROX),
                new ParseCase("parse-16", "1 lạng", new BigDecimal("1"), "mass", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-17", "2 lang", new BigDecimal("2"), "mass", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-18", "1 muỗng", new BigDecimal("1"), "volume", QuantityParsingService.ParseStatus.APPROX),
                new ParseCase("parse-19", "2 muong", new BigDecimal("2"), "volume", QuantityParsingService.ParseStatus.APPROX),
                new ParseCase("parse-20", "5 kg", new BigDecimal("5"), "mass", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-21", "6 gram", new BigDecimal("6"), "mass", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-22", "1 piece", new BigDecimal("1"), "count", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-23", "2 pcs", new BigDecimal("2"), "count", QuantityParsingService.ParseStatus.PARSED),
                new ParseCase("parse-24", "3 tbsp", new BigDecimal("3"), "volume", QuantityParsingService.ParseStatus.APPROX)
        );
        assertEquals(24, cases.size());
        return cases;
    }

    private void seedIngredientDictionary() {
        IngredientCanonical otHiem = canonical("ot_hiem", "spice", "count", null);
        IngredientCanonical ngheBot = canonical("nghe_bot", "spice", "mass", null);
        IngredientCanonical caChua = canonical("ca_chua", "vegetable", "count", new BigDecimal("150"));
        IngredientCanonical caiThia = canonical("cai_thia", "vegetable", "count", new BigDecimal("180"));
        IngredientCanonical thitBam = canonical("thit_bam", "meat", "mass", null);
        IngredientCanonical tomThe = canonical("tom_the", "seafood", "count", new BigDecimal("20"));
        IngredientCanonical gaDui = canonical("ga_dui", "poultry", "count", new BigDecimal("200"));
        IngredientCanonical suonHeo = canonical("suon_heo", "meat", "mass", null);
        IngredientCanonical caLoc = canonical("ca_loc", "seafood", "count", new BigDecimal("700"));
        IngredientCanonical hanhLa = canonical("hanh_la", "herb", "count", new BigDecimal("10"));

        registerAlias(otHiem, "ot hiem trai", "ot hiem tuoi", "ot hiem", "ot xiem");
        registerAlias(ngheBot, "bot nghe", "nghe bot", "turmeric powder", "bot nghe nguyen chat");
        registerAlias(caChua, "ca chua", "tomato", "ca chua da lat", "ca chua beef");
        registerAlias(caiThia, "cai thia", "rau cai xanh", "bok choy", "rau cai");
        registerAlias(thitBam, "thit bam", "thit heo xay", "heo xay", "thit xay");
        registerAlias(tomThe, "tom tuoi", "tom the", "shrimp");
        registerAlias(gaDui, "dui ga", "thit ga", "ga tuoi");
        registerAlias(suonHeo, "suon heo", "suon");
        registerAlias(caLoc, "ca loc", "ca song");
        registerAlias(hanhLa, "hanh la", "la hanh");
    }

    private void seedUnitDictionary() {
        UnitCanonical kg = unit("kg", "mass", new BigDecimal("1000"), false);
        UnitCanonical gram = unit("g", "mass", BigDecimal.ONE, false);
        UnitCanonical piece = unit("piece", "count", BigDecimal.ONE, false);
        UnitCanonical bunch = unit("bunch", "count", BigDecimal.ONE, true);
        UnitCanonical pinch = unit("pinch", "mass", BigDecimal.ONE, true);
        UnitCanonical spoon = unit("spoon", "volume", new BigDecimal("15"), true);
        UnitCanonical lang = unit("lang", "mass", new BigDecimal("100"), false);

        registerUnitAlias(kg, "kg", "kilogram");
        registerUnitAlias(gram, "g", "gram");
        registerUnitAlias(piece, "qua", "trai", "cai", "piece", "pcs");
        registerUnitAlias(bunch, "bo", "bó");
        registerUnitAlias(pinch, "nhum", "nhúm");
        registerUnitAlias(spoon, "muong", "muỗng", "tbsp");
        registerUnitAlias(lang, "lang", "lạng");
    }

    private void seedProducts() {
        Category spice = category(1L, "spice");
        Category vegetable = category(2L, "vegetable");
        Category meat = category(3L, "meat");
        Category seafood = category(4L, "seafood");
        Category poultry = category(5L, "poultry");
        Category herb = category(6L, "herb");

        products = List.of(
                product(1L, "ot-hiem-001", "ot hiem trai tuoi 100g", spice),
                product(2L, "cherry-001", "cherry my hop 300g", vegetable),
                product(3L, "nghe-001", "bot nghe turmeric powder nguyen chat dh foods", spice),
                product(4L, "cachua-001", "ca chua tomato da lat tui 1kg", vegetable),
                product(5L, "caithia-001", "rau cai thia bok choy organic", vegetable),
                product(6L, "thitbam-001", "thit heo xay tuoi", meat),
                product(7L, "tom-001", "tom the shrimp tuoi", seafood),
                product(8L, "ga-001", "dui ga tuoi rut xuong", poultry),
                product(9L, "suon-001", "suon heo non", meat),
                product(10L, "caloc-001", "ca loc tuoi song", seafood),
                product(11L, "hanhla-001", "hanh la tuoi", herb)
        );

        mapCanonicalToProduct("ot_hiem", 1L);
        mapCanonicalToProduct("nghe_bot", 3L);
        mapCanonicalToProduct("ca_chua", 4L);
        mapCanonicalToProduct("cai_thia", 5L);
        mapCanonicalToProduct("thit_bam", 6L);
        mapCanonicalToProduct("tom_the", 7L);
        mapCanonicalToProduct("ga_dui", 8L);
        mapCanonicalToProduct("suon_heo", 9L);
        mapCanonicalToProduct("ca_loc", 10L);
        mapCanonicalToProduct("hanh_la", 11L);
    }

    private void mapCanonicalToProduct(String canonicalCode, Long productId) {
        IngredientCanonical canonical = ingredientAliasByNorm.values().stream()
                .map(IngredientAlias::getCanonical)
                .filter(Objects::nonNull)
                .filter(c -> canonicalCode.equals(c.getCanonicalCode()))
                .findFirst()
                .orElse(null);
        if (canonical == null || canonical.getId() == null) {
            return;
        }
        Product product = products.stream().filter(p -> productId.equals(p.getId())).findFirst().orElse(null);
        if (product == null) {
            return;
        }
        canonicalProductNodes.put(canonical.getId(), List.of(ProductNode.builder()
                .productId(product.getId())
                .name(product.getName())
                .productCode(product.getProductCode())
                .status(product.getStatus())
                .categoryName(product.getCategory() == null ? null : product.getCategory().getName())
                .build()));
    }

    private IngredientCanonical canonical(String code, String family, String dimension, BigDecimal avgWeightG) {
        return IngredientCanonical.builder()
                .id(Math.abs(code.hashCode()) + 1L)
                .canonicalCode(code)
                .canonicalNameVi(code)
                .ingredientFamily(family)
                .defaultDimension(dimension)
                .averageWeightPerUnitG(avgWeightG)
                .active(true)
                .build();
    }

    private void registerAlias(IngredientCanonical canonical, String... aliases) {
        for (String aliasRaw : aliases) {
            String norm = normalize(aliasRaw);
            IngredientAlias alias = IngredientAlias.builder()
                    .id((long) (ingredientAliasByNorm.size() + 1))
                    .canonical(canonical)
                    .aliasTextRaw(aliasRaw)
                    .aliasTextNorm(norm)
                    .lang("vi")
                    .source("seed")
                    .confidence(BigDecimal.ONE)
                    .active(true)
                    .build();
            ingredientAliasByNorm.put(norm, alias);
        }
    }

    private UnitCanonical unit(String code, String dimension, BigDecimal factor, boolean approximate) {
        return UnitCanonical.builder()
                .id(Math.abs(code.hashCode()) + 100L)
                .unitCode(code)
                .dimension(dimension)
                .toBaseFactor(factor)
                .baseUnitCode(dimension.equals("volume") ? "ml" : (dimension.equals("mass") ? "g" : "piece"))
                .approximate(approximate)
                .active(true)
                .build();
    }

    private void registerUnitAlias(UnitCanonical canonical, String... aliases) {
        for (String aliasRaw : aliases) {
            String norm = normalize(aliasRaw);
            UnitAlias alias = UnitAlias.builder()
                    .id((long) (unitAliasByNorm.size() + 1))
                    .unitCanonical(canonical)
                    .aliasTextRaw(aliasRaw)
                    .aliasTextNorm(norm)
                    .locale("vi")
                    .source("seed")
                    .confidence(BigDecimal.ONE)
                    .active(true)
                    .build();
            unitAliasByNorm.put(norm, alias);
        }
    }

    private MealIngredient ingredient(IngredientCanonical canonical, UnitCanonical unit, BigDecimal qty) {
        MealIngredient ingredient = new MealIngredient();
        ingredient.setCanonicalIngredient(canonical);
        ingredient.setQuantityUnitCanonical(unit);
        ingredient.setQuantityValue(qty);
        return ingredient;
    }

    private Product product(Long id, String code, String name, Category category) {
        return Product.builder()
                .id(id)
                .productCode(code)
                .name(name)
                .status("ACTIVE")
                .category(category)
                .build();
    }

    private Category category(Long id, String name) {
        return Category.builder()
                .id(id)
                .categoryCode(name.toUpperCase(Locale.ROOT))
                .name(name)
                .isActive(true)
                .build();
    }

    private String normalize(String input) {
        return normalizer.normalize(input);
    }

    private record MatchCase(
            String caseName,
            String input,
            String expectedCanonical,
            String expectedProductToken,
            String forbiddenToken
    ) {}

    private record BridgeCase(
            String caseName,
            MealIngredient ingredient,
            String expectedStatus,
            BigDecimal expectedMassG,
            String expectedReason
    ) {}

    private record ParseCase(
            String caseName,
            String input,
            BigDecimal expectedValue,
            String expectedDimension,
            QuantityParsingService.ParseStatus expectedStatus
    ) {}
}
