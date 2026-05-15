package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.jpa.*;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SeedService {
    private record SeedProduct(
            String code,
            String name,
            Category cat,
            String sku,
            String vName,
            String unit,
            BigDecimal price,
            int stock,
            String shortDesc,
            String desc,
            String originCountry,
            String image,
            boolean isStaple
    ) {}

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductNodeRepository productNodeRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private AIModelRepository aiModelRepository;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    @Autowired
    private UserNutritionProfileRepository nutritionProfileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate neo4jTransactionTemplate;

    @Autowired
    public void setNeo4jTransactionManager(
            @Qualifier("neo4jTransactionManager") PlatformTransactionManager neo4jTransactionManager
    ) {
        this.neo4jTransactionTemplate = new TransactionTemplate(neo4jTransactionManager);
    }

    @Transactional
    public void seedData() {
        // 0. Database Migration (Fix for Supabase/Hibernate column sync)
        migrateSchema();

        // 1. Roles
        if (roleRepository.count() == 0) {
            roleRepository.saveAll(List.of(
                    Role.builder().name("ADMIN").description("System Administrator").build(),
                    Role.builder().name("CUSTOMER").description("Regular Customer").build(),
                    Role.builder().name("STAFF").description("Store Staff").build()));
            System.out.println(">> Seeded Roles!");
        }

        // 2. Users
        Role customerRole = roleRepository.findByName("CUSTOMER").orElse(null);
        Role adminRole = roleRepository.findByName("ADMIN").orElse(null);

        if (customerRole != null && !userRepository.existsByEmail("customer.p0@smartgrocery.com")) {
            User user = User.builder()
                    .email("customer.p0@smartgrocery.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .fullName("P0 Customer")
                    .phone("0987654320")
                    .role(customerRole)
                    .status("ACTIVE")
                    .build();
            userRepository.save(user);

            userAddressRepository.save(UserAddress.builder()
                    .user(user)
                    .receiverName(user.getFullName())
                    .receiverPhone(user.getPhone())
                    .streetAddress("101 Đường Tôn Đức Thắng")
                    .ward("Phường Bến Nghé")
                    .district("Quận 1")
                    .city("TP. Hồ Chí Minh")
                    .isDefault(true)
                    .build());
            
            // Sample Nutrition Profile: Gym enthusiast, High Protein, Allergy to Seafood
            nutritionProfileRepository.save(UserNutritionProfile.builder()
                    .user(user)
                    .healthGoals("Tăng cơ, giảm mỡ (Gym)")
                    .dietaryPreference("High Protein / Low Carb")
                    .allergies("Hải sản, tôm, cua")
                    .bmi(java.math.BigDecimal.valueOf(24.5))
                    .build());
            
            System.out.println(">> Seeded P0 Customer & Nutrition Profile");
        }

        if (adminRole != null && !userRepository.existsByEmail("admin.p0@smartgrocery.com")) {
            userRepository.save(User.builder()
                    .email("admin.p0@smartgrocery.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .fullName("P0 Admin")
                    .phone("0900000000")
                    .role(adminRole)
                    .status("ACTIVE")
                    .build());
            System.out.println(">> Seeded P0 Admin");
        }

        // 3. AI Models
        if (aiModelRepository.count() == 0) {
            aiModelRepository.saveAll(List.of(
                    AIModel.builder().modelCode("GEMINI-1.5-FLASH").provider("GOOGLE").modelName("Gemini 1.5 Flash")
                            .modelType("CHAT").isActive(true).build(),
                    AIModel.builder().modelCode("TEXT-EMBEDDING-004").provider("GOOGLE").modelName("Text Embedding 004")
                            .modelType("EMBEDDING").isActive(true).build()));
        }

        // 4. Logistics
        Warehouse mainWarehouse = warehouseRepository.findByCode("WH_MAIN")
                .orElseGet(() -> warehouseRepository.save(Warehouse.builder().code("WH_MAIN").name("Kho Trung Tâm").location("TP. Thủ Đức").build()));

        if (supplierRepository.count() == 0) {
            supplierRepository.save(Supplier.builder().name("Nông Trại Xanh").contactPerson("Anh Minh").phone("0900112233").build());
        }

        // 5. Categories
        Category veg = categoryRepository.findByCategoryCode("CAT_VEG").orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_VEG").name("Rau củ tươi").description("Rau củ và nông sản tươi mỗi ngày").build()));
        Category fruit = categoryRepository.findByCategoryCode("CAT_FRUIT").orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_FRUIT").name("Trái cây").description("Trái cây nội địa và nhập khẩu").build()));
        Category meat = categoryRepository.findByCategoryCode("CAT_MEAT").orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_MEAT").name("Thịt & Hải sản").description("Thịt, trứng, thủy hải sản tươi sống").build()));
        Category dairy = categoryRepository.findByCategoryCode("CAT_DAIRY").orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_DAIRY").name("Sữa & Trứng").description("Sữa và các chế phẩm từ sữa").build()));
        Category staple = categoryRepository.findByCategoryCode("CAT_STAPLE").orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_STAPLE").name("Nhu yếu phẩm").description("Thực phẩm khô, gia vị, đồ hộp tiện lợi").build()));
        Category house = categoryRepository.findByCategoryCode("CAT_HOU").orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_HOU").name("Gia dụng").description("Đồ dùng gia đình và chăm sóc nhà cửa").build()));
        Category drink = categoryRepository.findByCategoryCode("CAT_DRINK").orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_DRINK").name("Đồ uống").description("Nước giải khát, trà, cà phê, bia rượu").build()));
        Category frozen = categoryRepository.findByCategoryCode("CAT_FROZEN").orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_FROZEN").name("Chế biến & Đông lạnh").description("Thực phẩm chế biến nhanh và đông lạnh").build()));
        Category snack = categoryRepository.findByCategoryCode("CAT_SNACK").orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_SNACK").name("Bánh kẹo & Ăn vặt").description("Bánh kẹo, snack, hạt dinh dưỡng").build()));
        Category personal = categoryRepository.findByCategoryCode("CAT_PERSONAL").orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_PERSONAL").name("Chăm sóc cá nhân & Em bé").description("Mỹ phẩm, vệ sinh cá nhân, đồ cho bé").build()));

        // 6. Products Catalog
        List<SeedProduct> catalog = new ArrayList<>();

        addSeedGroup(catalog, veg, "P_VEGF", "SKU_VF", "PACK", "Gói 500g", 120, 18000, "Việt Nam", Arrays.asList(
                "Cải thìa", "Xà lách lolo xanh", "Cà chua beef", "Dưa leo baby", "Khoai tây Đà Lạt", "Cà rốt", "Bông cải xanh (Súp lơ)",
                "Hành tây", "Ớt chuông đủ màu", "Nấm kim châm", "Bắp cải tím", "Khoai lang mật", "Bí đỏ tròn", "Măng tây", "Khổ qua (Mướp đắng)"
        ));
        addSeedGroup(catalog, fruit, "P_FRUITA", "SKU_FA", "BAG", "Túi 1kg", 110, 42000, "Việt Nam", Arrays.asList(
                "Táo Envy", "Chuối già Nam Mỹ", "Cam sành", "Nho mẫu đơn", "Bơ sáp", "Dưa hấu không hạt", "Xoài cát Hòa Lộc", "Dâu tây Đà Lạt",
                "Bưởi da xanh", "Kiwis vàng"
        ));
        addSeedGroup(catalog, meat, "P_MEATM", "SKU_MM", "PACK", "Khay 500g", 90, 98000, "Việt Nam", Arrays.asList(
                "Thịt heo xay", "Ba rọi heo (Ba chỉ)", "Sườn non heo", "Thăn bò Úc", "Bắp bò hoa", "Đùi gà góc tư", "Cánh gà tươi",
                "Trứng gà ta (Hộp 10 quả)", "Trứng vịt lộn", "Tôm thẻ chân trắng", "Cá hồi phi lê", "Cá thu cắt khúc", "Mực ống tươi", "Nghêu sạch", "Chả cá thát lát"
        ));
        addSeedGroup(catalog, dairy, "P_DAIRY", "SKU_DY", "BOX", "Hộp 1L", 95, 32000, "Việt Nam", Arrays.asList(
                "Sữa tươi tiệt trùng có đường", "Sữa hạt hạnh phúc (Sữa hạt)", "Sữa chua ăn có đường", "Sữa chua uống men sống", "Phô mai lát (Sandwich)",
                "Bơ lạt tự nhiên", "Kem tươi (Whipping cream)", "Váng sữa cho bé", "Sữa đặc có bóng", "Phô mai con bò cười"
        ));
        addSeedGroup(catalog, staple, "P_STAPLE", "SKU_ST", "PACK", "Gói 500g", 140, 22000, "Việt Nam", Arrays.asList(
                "Gạo ST25", "Mì tôm Hảo Hảo", "Miến dong", "Nước mắm cá cơm", "Dầu ăn hướng dương", "Hạt nêm từ thịt", "Đường tinh luyện", "Muối i-ốt",
                "Tương ớt Chinsu", "Nước tương đậu nành", "Bột ngọt (Mì chính)", "Tiêu đen xay", "Bột mì đa năng", "Hạt chia Úc", "Ngũ cốc yến mạch"
        ));
        addSeedGroup(catalog, frozen, "P_FROZEN", "SKU_FRZ", "PACK", "Gói 500g", 80, 38000, "Việt Nam", Arrays.asList(
                "Xúc xích Đức", "Cá viên chiên", "Há cảo tôm thịt", "Bánh bao nhân thịt trứng cút", "Pizza cấp đông", "Đậu hũ non", "Kim chi cải thảo",
                "Chả giò tôm cua", "Lạp xưởng tươi", "Thịt xông khói"
        ));
        addSeedGroup(catalog, drink, "P_DRINK", "SKU_DRK", "BOTTLE", "Chai/Lon 330ml", 180, 12000, "Việt Nam", Arrays.asList(
                "Coca-Cola (Lon 330ml)", "Nước khoáng Lavie", "Nước tăng lực Redbull", "Trà xanh đóng chai", "Bia Heineken", "Cà phê hòa tan 3in1",
                "Trà túi lọc thảo mộc", "Nước ép cam nguyên chất", "Rượu vang đỏ", "Soda không đường"
        ));
        addSeedGroup(catalog, snack, "P_SNACK", "SKU_SNK", "PACK", "Gói 200g", 140, 26000, "Việt Nam", Arrays.asList(
                "Bánh quy bơ OREO", "Khoai tây chiên Lay's", "Socola đen 70% cacao", "Hạt điều rang muối", "Khô gà lá chanh", "Kẹo dẻo trái cây",
                "Bánh bông lan cuộn", "Rau củ quả sấy khô", "Rong biển ăn liền", "Hạnh nhân rang bơ"
        ));
        addSeedGroup(catalog, house, "P_HOUSEA", "SKU_HA", "BOTTLE", "Chai/Gói 750ml", 120, 35000, "Việt Nam", Arrays.asList(
                "Nước giặt Ariel", "Nước rửa chén Sunlight", "Giấy vệ sinh (Cuộn)", "Dầu gội đầu thảo dược", "Kem đánh răng"
        ));
        addSeedGroup(catalog, veg, "P_VEGN", "SKU_VN", "PACK", "Gói 500g", 120, 17000, "Việt Nam", Arrays.asList(
                "Rau muống nước", "Cải bẹ xanh", "Đậu cô ve", "Bí ngòi xanh", "Bắp Mỹ (Ngô ngọt)", "Nấm đùi gà", "Rau mồng tơi", "Củ cải trắng",
                "Sả cây tươi", "Gừng già", "Ngò rí & Hành lá (Combo)", "Ớt hiểm trái", "Chanh không hạt", "Củ sen tươi", "Đậu bắp"
        ));
        addSeedGroup(catalog, fruit, "P_FRUITB", "SKU_FB", "BAG", "Túi 1kg", 110, 48000, "Việt Nam", Arrays.asList(
                "Việt quất tươi", "Lê Hàn Quốc", "Na Thái", "Thanh long ruột đỏ", "Măng cụt", "Chôm chôm nhãn", "Dưa lưới Huỳnh Long", "Đu đủ chín cây",
                "Lựu đỏ", "Ổi nữ hoàng"
        ));
        addSeedGroup(catalog, meat, "P_SEA", "SKU_SEA", "PACK", "Khay 500g", 80, 125000, "Việt Nam", Arrays.asList(
                "Cua Cà Mau", "Bạch tuộc mini", "Cá lóc đồng làm sẵn", "Cá điêu hồng phi lê", "Tôm hùm Alaska", "Ốc hương", "Lươn đồng làm sạch",
                "Cá nục bông", "Hàu sữa Pháp (Tách vỏ)", "Chả lụa que"
        ));
        addSeedGroup(catalog, dairy, "P_DAIRYB", "SKU_DB", "BOX", "Hộp/Gói 500g", 95, 34000, "Việt Nam", Arrays.asList(
                "Sữa tươi tách béo", "Sữa đậu nành nguyên chất", "Kem hộp vị Vanilla", "Sữa chua không đường", "Phô mai Mozzarella bào sợi",
                "Đậu hũ chiên sẵn", "Sườn non chay", "Chả lụa chay", "Nấm tuyết khô", "Sữa hạnh nhân không đường"
        ));
        addSeedGroup(catalog, staple, "P_STAPLEB", "SKU_SB", "PACK", "Gói/Chai 500g", 140, 26000, "Việt Nam", Arrays.asList(
                "Dầu hào Maggi", "Giấm táo", "Sốt Mayonnaise", "Bột bắp", "Ngũ vị hương", "Mật ong hoa nhãn", "Sốt BBQ ướp thịt", "Bột cà ri",
                "Tương đen ăn phở", "Chao môn", "Dầu mè thơm", "Me cục nấu canh chua", "Bột nghệ nguyên chất", "Màu dầu điều", "Muối tôm Tây Ninh"
        ));
        addSeedGroup(catalog, staple, "P_CONV", "SKU_CV", "PACK", "Hộp/Gói tiện lợi", 150, 28000, "Việt Nam", Arrays.asList(
                "Cá nục sốt cà chua đóng hộp", "Thịt hộp Spam", "Pate gan heo", "Ngô ngọt đóng lon", "Đậu nành lên men (Natty)", "Cháo gói ăn liền",
                "Bún tươi sấy khô", "Phở bò ăn liền (Dạng tô)", "Rong biển nấu canh", "Kim chi giá đỗ"
        ));
        addSeedGroup(catalog, personal, "P_PERSON", "SKU_PER", "PACK", "Gói/Chai cá nhân", 150, 30000, "Việt Nam", Arrays.asList(
                "Sữa tắm dưỡng ẩm", "Sữa rửa mặt cho da dầu", "Kem chống nắng", "Bàn chải đánh răng mềm", "Nước súc miệng", "Dao cạo râu",
                "Băng vệ sinh (Gói)", "Tã quần cho bé (Size L)", "Khăn ướt em bé", "Sữa bột công thức", "Bánh ăn dặm", "Phấn rôm",
                "Dung dịch vệ sinh phụ nữ", "Nước rửa tay sát khuẩn", "Bông tẩy trang"
        ));
        addSeedGroup(catalog, house, "P_HOUSEB", "SKU_HB", "PACK", "Gói/Chai gia dụng", 160, 32000, "Việt Nam", Arrays.asList(
                "Nước xả vải thơm lâu", "Nước lau sàn hương quế", "Nước tẩy nhà vệ sinh", "Nước lau kính", "Xịt côn trùng", "Túi rác đen (Cuộn)",
                "Màng bọc thực phẩm", "Giấy bạc nướng thức ăn", "Miếng rửa chén (Combo 3 miếng)", "Găng tay cao su", "Sáp thơm phòng",
                "Nước tẩy trắng quần áo", "Nước lau bếp đa năng", "Cọ quét nhà (Chổi)", "Pin AA (Vỉ 4 viên)"
        ));

        for (SeedProduct sp : catalog) {
            Product product = productRepository.findByProductCode(sp.code)
                    .orElseGet(() -> productRepository.save(Product.builder()
                            .productCode(sp.code)
                            .name(sp.name)
                            .category(sp.cat)
                            .shortDescription(sp.shortDesc)
                            .description(sp.desc)
                            .originCountry(sp.originCountry)
                            .image(sp.image)
                            .status("ACTIVE")
                            .isFeatured(false)
                            .isStaple(sp.isStaple)
                            .build()));

            boolean productChanged = false;
            if (product.getCategory() == null) { product.setCategory(sp.cat); productChanged = true; }
            if (product.getName() == null || product.getName().isBlank()) { product.setName(sp.name); productChanged = true; }
            if (product.getShortDescription() == null || product.getShortDescription().isBlank()) { product.setShortDescription(sp.shortDesc); productChanged = true; }
            if (product.getDescription() == null || product.getDescription().isBlank()) { product.setDescription(sp.desc); productChanged = true; }
            if (product.getOriginCountry() == null || product.getOriginCountry().isBlank()) { product.setOriginCountry(sp.originCountry); productChanged = true; }
            if (product.getImage() == null || product.getImage().isBlank()) { product.setImage(sp.image); productChanged = true; }
            if (product.getStatus() == null || product.getStatus().isBlank()) { product.setStatus("ACTIVE"); productChanged = true; }
            if (product.getIsFeatured() == null) { product.setIsFeatured(false); productChanged = true; }
            if (productChanged) productRepository.save(product);

            ProductVariant variant = productVariantRepository.findBySku(sp.sku)
                    .orElseGet(() -> productVariantRepository.save(ProductVariant.builder()
                            .product(product)
                            .sku(sp.sku)
                            .barcode("BAR_" + sp.sku)
                            .variantName(sp.vName)
                            .unit(sp.unit)
                            .packageSize(sp.vName)
                            .weightGram(estimateWeightGram(sp.vName))
                            .netPrice(sp.price)
                            .compareAtPrice(sp.price.multiply(BigDecimal.valueOf(1.1)))
                            .costPrice(sp.price.multiply(BigDecimal.valueOf(0.78)))
                            .vatPercent(BigDecimal.valueOf(8))
                            .status("ACTIVE")
                            .build()));

            boolean variantChanged = false;
            if (variant.getProduct() == null) { variant.setProduct(product); variantChanged = true; }
            if (variant.getBarcode() == null || variant.getBarcode().isBlank()) { variant.setBarcode("BAR_" + sp.sku); variantChanged = true; }
            if (variant.getVariantName() == null || variant.getVariantName().isBlank()) { variant.setVariantName(sp.vName); variantChanged = true; }
            if (variant.getUnit() == null || variant.getUnit().isBlank()) { variant.setUnit(sp.unit); variantChanged = true; }
            if (variant.getPackageSize() == null || variant.getPackageSize().isBlank()) { variant.setPackageSize(sp.vName); variantChanged = true; }
            if (variant.getWeightGram() == null || variant.getWeightGram() <= 0) { variant.setWeightGram(estimateWeightGram(sp.vName)); variantChanged = true; }
            if (variant.getNetPrice() == null || variant.getNetPrice().compareTo(BigDecimal.ZERO) <= 0) { variant.setNetPrice(sp.price); variantChanged = true; }
            if (variant.getCompareAtPrice() == null || variant.getCompareAtPrice().compareTo(BigDecimal.ZERO) <= 0) {
                variant.setCompareAtPrice(sp.price.multiply(BigDecimal.valueOf(1.1))); variantChanged = true;
            }
            if (variant.getCostPrice() == null || variant.getCostPrice().compareTo(BigDecimal.ZERO) <= 0) {
                variant.setCostPrice(sp.price.multiply(BigDecimal.valueOf(0.78))); variantChanged = true;
            }
            if (variant.getVatPercent() == null) { variant.setVatPercent(BigDecimal.valueOf(8)); variantChanged = true; }
            if (variant.getStatus() == null || variant.getStatus().isBlank()) { variant.setStatus("ACTIVE"); variantChanged = true; }
            if (variantChanged) productVariantRepository.save(variant);

            InventoryStock inventoryStock = inventoryStockRepository.findByWarehouseIdAndVariantId(mainWarehouse.getId(), variant.getId())
                    .orElseGet(() -> InventoryStock.builder()
                            .warehouse(mainWarehouse)
                            .variant(variant)
                            .availableQuantity(sp.stock)
                            .reservedQuantity(0)
                            .build());
            boolean stockChanged = false;
            if (inventoryStock.getWarehouse() == null) { inventoryStock.setWarehouse(mainWarehouse); stockChanged = true; }
            if (inventoryStock.getVariant() == null) { inventoryStock.setVariant(variant); stockChanged = true; }
            if (inventoryStock.getAvailableQuantity() == null || inventoryStock.getAvailableQuantity() < 0) {
                inventoryStock.setAvailableQuantity(sp.stock); stockChanged = true;
            }
            if (inventoryStock.getReservedQuantity() == null || inventoryStock.getReservedQuantity() < 0) {
                inventoryStock.setReservedQuantity(0); stockChanged = true;
            }
            if (inventoryStock.getId() == null || stockChanged) inventoryStockRepository.save(inventoryStock);
        }

        // 6.1 Self-heal toàn bộ dữ liệu cũ để đảm bảo không còn null ngoài catalog hiện tại
        backfillNullsForAllExistingProducts(staple);
        backfillNullsForAllExistingVariants();
        ensureInventoryStocksForAllVariants(mainWarehouse);

        // 7. Sync Neo4j
        try {
            neo4jTransactionTemplate.executeWithoutResult(status -> {
                productRepository.findAll().forEach(p -> {
                    productNodeRepository.save(ProductNode.builder()
                            .productId(p.getId())
                            .name(p.getName() != null ? p.getName() : "Sản phẩm")
                            .description(p.getDescription() != null ? p.getDescription() : "Chưa có mô tả")
                            .price(0.0)
                            .build());
                });
            });
        } catch (Exception e) {
            System.err.println("Neo4j sync skipped: " + e.getMessage());
        }

        logSeedDataQuality();
        System.out.println(">> Seed complete with " + catalog.size() + " products!");
    }

    private void logSeedDataQuality() {
        List<Product> products = productRepository.findAll();
        List<ProductVariant> variants = productVariantRepository.findAll();
        List<InventoryStock> stocks = inventoryStockRepository.findAll();

        List<String> productsWithNull = products.stream()
                .filter(p -> p.getProductCode() == null || p.getName() == null || p.getCategory() == null || p.getDescription() == null
                        || p.getShortDescription() == null || p.getOriginCountry() == null || p.getImage() == null
                        || p.getStatus() == null || p.getIsFeatured() == null)
                .map(p -> p.getProductCode() + " | " + p.getName())
                .collect(Collectors.toList());

        List<String> variantsWithNull = variants.stream()
                .filter(v -> v.getProduct() == null || v.getSku() == null || v.getBarcode() == null || v.getVariantName() == null
                        || v.getUnit() == null || v.getPackageSize() == null || v.getWeightGram() == null
                        || v.getNetPrice() == null || v.getCompareAtPrice() == null || v.getCostPrice() == null
                        || v.getVatPercent() == null || v.getStatus() == null)
                .map(v -> v.getSku() + " | " + (v.getProduct() != null ? v.getProduct().getName() : "NO_PRODUCT"))
                .collect(Collectors.toList());

        List<String> stocksWithNull = stocks.stream()
                .filter(s -> s.getWarehouse() == null || s.getVariant() == null || s.getAvailableQuantity() == null || s.getReservedQuantity() == null)
                .map(s -> "stockId=" + s.getId())
                .collect(Collectors.toList());

        System.out.println(">> DATA QUALITY CHECK:");
        System.out.println("   - products.count = " + products.size());
        System.out.println("   - variants.count = " + variants.size());
        System.out.println("   - inventory_stocks.count = " + stocks.size());
        System.out.println("   - products_with_null = " + productsWithNull.size());
        if (!productsWithNull.isEmpty()) {
            System.out.println("   - products_with_null_list = " + productsWithNull);
        }
        System.out.println("   - variants_with_null = " + variantsWithNull.size());
        if (!variantsWithNull.isEmpty()) {
            System.out.println("   - variants_with_null_list = " + variantsWithNull);
        }
        System.out.println("   - stocks_with_null = " + stocksWithNull.size());
        if (!stocksWithNull.isEmpty()) {
            System.out.println("   - stocks_with_null_list = " + stocksWithNull);
        }
    }

    private void addSeedGroup(
            List<SeedProduct> catalog,
            Category category,
            String codePrefix,
            String skuPrefix,
            String unit,
            String variantName,
            int stock,
            int basePrice,
            String originCountry,
            List<String> names
    ) {
        // Automatically mark Staple category as staples, or individual items like oil/salt
        boolean isStapleCategory = category.getCategoryCode().equals("CAT_STAPLE");
        
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String code = codePrefix + "_" + String.format("%03d", i + 1);
            String sku = skuPrefix + "_" + String.format("%03d", i + 1);
            BigDecimal price = BigDecimal.valueOf(basePrice + ((i % 5) * 2500L));
            String shortDesc = "Sản phẩm " + name + " chất lượng, nguồn gốc rõ ràng.";
            String desc = shortDesc + " Phù hợp cho nhu cầu tiêu dùng hằng ngày tại SmartGrocery.";
            String image = "/uploads/products/" + sku.toLowerCase() + ".jpg";
            
            boolean itemIsStaple = isStapleCategory || 
                                 name.contains("Gạo") || 
                                 name.contains("Dầu ăn") || 
                                 name.contains("Muối") || 
                                 name.contains("Đường") || 
                                 name.contains("Nước mắm") ||
                                 name.contains("Bột ngọt");
            
            catalog.add(new SeedProduct(code, name, category, sku, variantName, unit, price, stock, shortDesc, desc, originCountry, image, itemIsStaple));
        }
    }

    private void migrateSchema() {
        try {
            // Check if column exists, if not add it
            jdbcTemplate.execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS is_staple BOOLEAN DEFAULT FALSE");
            System.out.println(">> Database Migration: Verified/Added is_staple column to products table.");
        } catch (Exception e) {
            System.err.println(">> Database Migration Notice: " + e.getMessage());
        }
    }

    private void backfillNullsForAllExistingProducts(Category fallbackCategory) {
        List<Product> allProducts = productRepository.findAll();
        for (Product product : allProducts) {
            boolean changed = false;

            if (product.getProductCode() == null || product.getProductCode().isBlank()) {
                String idPart = product.getId() != null ? String.valueOf(product.getId()) : String.valueOf(System.nanoTime());
                product.setProductCode("P_AUTO_" + idPart);
                changed = true;
            }
            if (product.getName() == null || product.getName().isBlank()) {
                product.setName("Sản phẩm SmartGrocery");
                changed = true;
            }
            if (product.getCategory() == null) {
                product.setCategory(fallbackCategory);
                changed = true;
            }
            if (product.getShortDescription() == null || product.getShortDescription().isBlank()) {
                product.setShortDescription("Sản phẩm chất lượng, phù hợp nhu cầu tiêu dùng hằng ngày.");
                changed = true;
            }
            if (product.getDescription() == null || product.getDescription().isBlank()) {
                product.setDescription("Sản phẩm SmartGrocery đã được chuẩn hóa dữ liệu để đảm bảo đầy đủ thông tin.");
                changed = true;
            }
            if (product.getOriginCountry() == null || product.getOriginCountry().isBlank()) {
                product.setOriginCountry("Việt Nam");
                changed = true;
            }
            if (product.getImage() == null || product.getImage().isBlank()) {
                product.setImage("/uploads/products/default.jpg");
                changed = true;
            }
            if (product.getStatus() == null || product.getStatus().isBlank()) {
                product.setStatus("ACTIVE");
                changed = true;
            }
            if (product.getIsFeatured() == null) {
                product.setIsFeatured(false);
                changed = true;
            }

            if (changed) {
                productRepository.save(product);
            }
        }
    }

    private void backfillNullsForAllExistingVariants() {
        List<ProductVariant> allVariants = productVariantRepository.findAll();
        Product fallbackProduct = productRepository.findAll().stream().findFirst().orElse(null);
        for (ProductVariant variant : allVariants) {
            boolean changed = false;

            if (variant.getProduct() == null && fallbackProduct != null) {
                variant.setProduct(fallbackProduct);
                changed = true;
            }
            if (variant.getSku() == null || variant.getSku().isBlank()) {
                String idPart = variant.getId() != null ? String.valueOf(variant.getId()) : String.valueOf(System.nanoTime());
                variant.setSku("SKU_AUTO_" + idPart);
                changed = true;
            }
            if (variant.getBarcode() == null || variant.getBarcode().isBlank()) {
                variant.setBarcode("BAR_" + variant.getSku());
                changed = true;
            }
            if (variant.getVariantName() == null || variant.getVariantName().isBlank()) {
                variant.setVariantName("Quy cách tiêu chuẩn");
                changed = true;
            }
            if (variant.getUnit() == null || variant.getUnit().isBlank()) {
                variant.setUnit("UNIT");
                changed = true;
            }
            if (variant.getPackageSize() == null || variant.getPackageSize().isBlank()) {
                variant.setPackageSize(variant.getVariantName());
                changed = true;
            }
            if (variant.getWeightGram() == null || variant.getWeightGram() <= 0) {
                variant.setWeightGram(estimateWeightGram(variant.getVariantName()));
                changed = true;
            }
            if (variant.getNetPrice() == null || variant.getNetPrice().compareTo(BigDecimal.ZERO) <= 0) {
                variant.setNetPrice(BigDecimal.valueOf(10000));
                changed = true;
            }
            if (variant.getCompareAtPrice() == null || variant.getCompareAtPrice().compareTo(BigDecimal.ZERO) <= 0) {
                variant.setCompareAtPrice(variant.getNetPrice().multiply(BigDecimal.valueOf(1.1)));
                changed = true;
            }
            if (variant.getCostPrice() == null || variant.getCostPrice().compareTo(BigDecimal.ZERO) <= 0) {
                variant.setCostPrice(variant.getNetPrice().multiply(BigDecimal.valueOf(0.78)));
                changed = true;
            }
            if (variant.getVatPercent() == null) {
                variant.setVatPercent(BigDecimal.valueOf(8));
                changed = true;
            }
            if (variant.getStatus() == null || variant.getStatus().isBlank()) {
                variant.setStatus("ACTIVE");
                changed = true;
            }

            if (changed) {
                productVariantRepository.save(variant);
            }
        }
    }

    private void ensureInventoryStocksForAllVariants(Warehouse mainWarehouse) {
        List<ProductVariant> allVariants = productVariantRepository.findAll();
        for (ProductVariant variant : allVariants) {
            InventoryStock stock = inventoryStockRepository.findByWarehouseIdAndVariantId(mainWarehouse.getId(), variant.getId())
                    .orElseGet(() -> InventoryStock.builder()
                            .warehouse(mainWarehouse)
                            .variant(variant)
                            .availableQuantity(100)
                            .reservedQuantity(0)
                            .build());

            boolean changed = false;
            if (stock.getWarehouse() == null) {
                stock.setWarehouse(mainWarehouse);
                changed = true;
            }
            if (stock.getVariant() == null) {
                stock.setVariant(variant);
                changed = true;
            }
            if (stock.getAvailableQuantity() == null || stock.getAvailableQuantity() < 0) {
                stock.setAvailableQuantity(100);
                changed = true;
            }
            if (stock.getReservedQuantity() == null || stock.getReservedQuantity() < 0) {
                stock.setReservedQuantity(0);
                changed = true;
            }

            if (stock.getId() == null || changed) {
                inventoryStockRepository.save(stock);
            }
        }
    }

    private int estimateWeightGram(String variantName) {
        if (variantName == null) return 500;
        String n = variantName.toLowerCase();
        if (n.contains("1kg")) return 1000;
        if (n.contains("750ml")) return 750;
        if (n.contains("500g")) return 500;
        if (n.contains("330ml")) return 330;
        if (n.contains("250g")) return 250;
        if (n.contains("200g")) return 200;
        return 500;
    }
}
