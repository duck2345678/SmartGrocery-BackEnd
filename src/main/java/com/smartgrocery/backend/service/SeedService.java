package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.CreateOrderRequest;
import com.smartgrocery.backend.dto.OrderItemRequest;
import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.*;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SeedService {

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
    private VoucherRepository voucherRepository;

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    private TransactionTemplate neo4jTransactionTemplate;

    @Autowired
    public void setNeo4jTransactionManager(
            @Qualifier("neo4jTransactionManager") PlatformTransactionManager neo4jTransactionManager
    ) {
        this.neo4jTransactionTemplate = new TransactionTemplate(neo4jTransactionManager);
    }

    @Transactional
    public void seedData() {
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
        Role staffRole = roleRepository.findByName("STAFF").orElse(null);
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
            System.out.println(">> Seeded P0 Customer: customer.p0@smartgrocery.com / password123");
        }

        if (staffRole != null && !userRepository.existsByEmail("staff.p0@smartgrocery.com")) {
            User staff = User.builder()
                    .email("staff.p0@smartgrocery.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .fullName("P0 Staff")
                    .phone("0912345678")
                    .role(staffRole)
                    .status("ACTIVE")
                    .build();
            userRepository.save(staff);
            System.out.println(">> Seeded P0 Staff: staff.p0@smartgrocery.com / password123");
        }

        if (adminRole != null && !userRepository.existsByEmail("admin.p0@smartgrocery.com")) {
            User admin = User.builder()
                    .email("admin.p0@smartgrocery.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .fullName("P0 Admin")
                    .phone("0900000000")
                    .role(adminRole)
                    .status("ACTIVE")
                    .build();
            userRepository.save(admin);
            System.out.println(">> Seeded P0 Admin: admin.p0@smartgrocery.com / password123");
        }

        // 3. AI Models
        if (aiModelRepository.count() == 0) {
            aiModelRepository.saveAll(List.of(
                    AIModel.builder().modelCode("GEMINI-1.5-FLASH").provider("GOOGLE").modelName("Gemini 1.5 Flash")
                            .modelType("CHAT").isActive(true).build(),
                    AIModel.builder().modelCode("TEXT-EMBEDDING-004").provider("GOOGLE").modelName("Text Embedding 004")
                            .modelType("EMBEDDING").isActive(true).build()));
            System.out.println(">> Seeded AI Models!");
        }

        // 4. Warehouses & Suppliers
        if (warehouseRepository.count() == 0) {
            warehouseRepository
                    .save(Warehouse.builder().code("WH_MAIN").name("Kho Trung Tâm").location("TP. Thủ Đức").build());
            supplierRepository.save(
                    Supplier.builder().name("Nông Trại Xanh").contactPerson("Anh Minh").phone("0900112233").build());
            System.out.println(">> Seeded Logistics!");
        }

        // 5. Catalog
        Warehouse mainWarehouse = warehouseRepository.findAll().stream().findFirst()
                .orElseGet(() -> warehouseRepository
                        .save(Warehouse.builder().code("WH_MAIN").name("Kho Trung Tâm").location("TP. Thủ Đức").build()));

        Category veg = categoryRepository.findByCategoryCode("CAT_VEG")
                .orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_VEG").name("Rau củ").build()));
        Category fruit = categoryRepository.findByCategoryCode("CAT_FRUIT")
                .orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_FRUIT").name("Trái cây").build()));
        Category dairy = categoryRepository.findByCategoryCode("CAT_DAIRY")
                .orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_DAIRY").name("Sữa & trứng").build()));
        Category meat = categoryRepository.findByCategoryCode("CAT_MEAT")
                .orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_MEAT").name("Thịt & hải sản").build()));
        Category staple = categoryRepository.findByCategoryCode("CAT_STAPLE")
                .orElseGet(() -> categoryRepository.save(Category.builder().categoryCode("CAT_STAPLE").name("Nhu yếu phẩm").build()));

        record SeedProduct(String code, String name, Category category, String sku, String barcode, String variantName, String unit, BigDecimal price, int stock) {}
        List<SeedProduct> catalog = List.of(
                new SeedProduct("P_BROC", "Bông cải xanh", veg, "SKU_B001", "BAR_B001", "Bông cải xanh 500g", "PACK", BigDecimal.valueOf(30000), 40),
                new SeedProduct("P_CARROT", "Cà rốt Đà Lạt", veg, "SKU_V002", "BAR_V002", "Cà rốt 1kg", "BAG", BigDecimal.valueOf(28000), 35),
                new SeedProduct("P_TOMATO", "Cà chua bi", veg, "SKU_V003", "BAR_V003", "Cà chua bi 500g", "BOX", BigDecimal.valueOf(25000), 25),
                new SeedProduct("P_APPLE", "Táo đỏ Mỹ", fruit, "SKU_F001", "BAR_F001", "Táo đỏ 1kg", "BAG", BigDecimal.valueOf(68000), 20),
                new SeedProduct("P_BANANA", "Chuối già Nam Mỹ", fruit, "SKU_F002", "BAR_F002", "Chuối 1 nải", "BUNCH", BigDecimal.valueOf(22000), 30),
                new SeedProduct("P_ORANGE", "Cam sành", fruit, "SKU_F003", "BAR_F003", "Cam 1kg", "BAG", BigDecimal.valueOf(42000), 18),
                new SeedProduct("P_MILK", "Sữa tươi không đường", dairy, "SKU_D001", "BAR_D001", "Hộp 1L", "BOX", BigDecimal.valueOf(33000), 60),
                new SeedProduct("P_EGGS", "Trứng gà", dairy, "SKU_D002", "BAR_D002", "Vỉ 10 trứng", "TRAY", BigDecimal.valueOf(32000), 50),
                new SeedProduct("P_PORK", "Thịt heo ba rọi", meat, "SKU_M001", "BAR_M001", "500g", "PACK", BigDecimal.valueOf(75000), 15),
                new SeedProduct("P_RICE", "Gạo ST25", staple, "SKU_S001", "BAR_S001", "Túi 5kg", "BAG", BigDecimal.valueOf(165000), 12)
        );

        int createdCount = 0;
        Map<String, BigDecimal> compareAtPriceBySku = new HashMap<>();
        compareAtPriceBySku.put("SKU_B001", BigDecimal.valueOf(39000));   // Bông cải xanh: 30k -> 39k
        compareAtPriceBySku.put("SKU_F001", BigDecimal.valueOf(82000));   // Táo đỏ Mỹ: 68k -> 82k
        compareAtPriceBySku.put("SKU_D001", BigDecimal.valueOf(39000));   // Sữa tươi không đường: 33k -> 39k
        compareAtPriceBySku.put("SKU_M001", BigDecimal.valueOf(89000));   // Thịt heo ba rọi: 75k -> 89k
        compareAtPriceBySku.put("SKU_S001", BigDecimal.valueOf(185000));  // Gạo ST25: 165k -> 185k
        for (SeedProduct sp : catalog) {
            Product product = productRepository.findByProductCode(sp.code)
                    .orElseGet(() -> {
                        Product p = Product.builder()
                                .productCode(sp.code)
                                .name(sp.name)
                                .category(sp.category)
                                .shortDescription("Hàng mới về")
                                .status("ACTIVE")
                                .isFeatured(false)
                                .build();
                        return productRepository.save(p);
                    });

            ProductVariant variant = productVariantRepository.findBySku(sp.sku)
                    .orElseGet(() -> productVariantRepository.save(ProductVariant.builder()
                            .product(product)
                            .sku(sp.sku)
                            .barcode(sp.barcode)
                            .variantName(sp.variantName)
                            .unit(sp.unit)
                            .netPrice(sp.price)
                            .status("ACTIVE")
                            .build()));

            // Ensure several products always have real discount prices in DB for Home "Giảm giá hot".
            BigDecimal compareAtPrice = compareAtPriceBySku.get(sp.sku);
            if (compareAtPrice != null && compareAtPrice.compareTo(sp.price) > 0) {
                if (variant.getCompareAtPrice() == null || variant.getCompareAtPrice().compareTo(compareAtPrice) != 0) {
                    variant.setCompareAtPrice(compareAtPrice);
                    productVariantRepository.save(variant);
                }
            }

            inventoryStockRepository.findByWarehouseIdAndVariantId(mainWarehouse.getId(), variant.getId())
                    .orElseGet(() -> inventoryStockRepository.save(InventoryStock.builder()
                            .warehouse(mainWarehouse)
                            .variant(variant)
                            .availableQuantity(sp.stock)
                            .reservedQuantity(0)
                            .build()));

            createdCount++;
        }
        if (createdCount > 0) System.out.println(">> Seeded Catalog (10 products + inventory)!");

        // 5.1 Long-name product for UI overflow testing
        Product longNameProduct = productRepository.findByProductCode("P_LONG_NAME")
                .orElseGet(() -> productRepository.save(Product.builder()
                        .productCode("P_LONG_NAME")
                        .name("Nước rửa chén siêu đậm đặc hương chanh tươi mát phiên bản giới hạn dùng để test tên sản phẩm dài 3 dòng trên UI")
                        .category(staple)
                        .shortDescription("Dùng để test overflow/ellipsis UI")
                        .status("ACTIVE")
                        .isFeatured(false)
                        .build()));

        ProductVariant longNameVariant = productVariantRepository.findBySku("SKU_LONG_001")
                .orElseGet(() -> productVariantRepository.save(ProductVariant.builder()
                        .product(longNameProduct)
                        .sku("SKU_LONG_001")
                        .barcode("BAR_LONG_001")
                        .variantName("Chai 750ml")
                        .unit("BOTTLE")
                        .aisleLocation("Z9")
                        .netPrice(BigDecimal.valueOf(89000))
                        .status("ACTIVE")
                        .build()));

        inventoryStockRepository.findByWarehouseIdAndVariantId(mainWarehouse.getId(), longNameVariant.getId())
                .orElseGet(() -> inventoryStockRepository.save(InventoryStock.builder()
                        .warehouse(mainWarehouse)
                        .variant(longNameVariant)
                        .availableQuantity(200)
                        .reservedQuantity(0)
                        .build()));

        // 5.2 Fulfillment test orders (real DB data for Staff UI testing)
        seedFulfillmentOrdersIfNeeded(longNameVariant.getId());

        // 6. Graph (Neo4j)
        neo4jTransactionTemplate.executeWithoutResult(status -> {
            if (productNodeRepository.count() == 0) {
                productRepository.findAll().forEach(p -> {
                    ProductNode node = ProductNode.builder()
                            .productId(p.getId())
                            .name(p.getName())
                            .build();
                    productNodeRepository.save(node);
                });
                System.out.println(">> Synchronized Neo4j!");
            }
        });
    }

    private void seedFulfillmentOrdersIfNeeded(Long longNameVariantId) {
        if (orderRepository.count() >= 6) {
            return;
        }

        User customer = userRepository.findByEmail("customer.p0@smartgrocery.com").orElse(null);
        if (customer == null) return;

        Long addressId = userAddressRepository.findByUser_Id(customer.getId()).stream()
                .findFirst()
                .map(UserAddress::getId)
                .orElse(null);

        List<ProductVariant> baseVariants = List.of(
                productVariantRepository.findBySku("SKU_B001").orElse(null),
                productVariantRepository.findBySku("SKU_V002").orElse(null),
                productVariantRepository.findBySku("SKU_V003").orElse(null),
                productVariantRepository.findBySku("SKU_F001").orElse(null),
                productVariantRepository.findBySku("SKU_F002").orElse(null),
                productVariantRepository.findBySku("SKU_F003").orElse(null),
                productVariantRepository.findBySku("SKU_D001").orElse(null),
                productVariantRepository.findBySku("SKU_D002").orElse(null),
                productVariantRepository.findBySku("SKU_M001").orElse(null),
                productVariantRepository.findBySku("SKU_S001").orElse(null)
        ).stream().filter(v -> v != null).toList();

        if (baseVariants.size() < 6) return;

        createSeedOrder(customer, addressId, "COD", "SEED_UI_SMALL_1_ITEM", List.of(
                OrderItemRequest.builder()
                        .variantId(baseVariants.get(0).getId())
                        .quantity(1)
                        .allowSubstitution(false)
                        .build()
        ));

        createSeedOrder(customer, addressId, "COD", "SEED_UI_MIXED_ALLOW_SUB", List.of(
                OrderItemRequest.builder().variantId(baseVariants.get(1).getId()).quantity(2).allowSubstitution(true).build(),
                OrderItemRequest.builder().variantId(baseVariants.get(2).getId()).quantity(1).allowSubstitution(false).build(),
                OrderItemRequest.builder().variantId(baseVariants.get(3).getId()).quantity(1).allowSubstitution(true).build(),
                OrderItemRequest.builder().variantId(baseVariants.get(4).getId()).quantity(3).allowSubstitution(false).build(),
                OrderItemRequest.builder().variantId(baseVariants.get(5).getId()).quantity(1).allowSubstitution(true).build()
        ));

        createSeedOrder(customer, addressId, "COD", "SEED_UI_LONG_NAME_OVERFLOW", List.of(
                OrderItemRequest.builder().variantId(longNameVariantId).quantity(1).allowSubstitution(false).build(),
                OrderItemRequest.builder().variantId(baseVariants.get(6).getId()).quantity(1).allowSubstitution(false).build()
        ));

        List<OrderItemRequest> bigItems = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            ProductVariant v = baseVariants.get(i % baseVariants.size());
            bigItems.add(OrderItemRequest.builder()
                    .variantId(v.getId())
                    .quantity(1)
                    .allowSubstitution(i % 3 == 0)
                    .build());
        }
        createSeedOrder(customer, addressId, "COD", "SEED_UI_BIG_50_LINES", bigItems);
    }

    private void createSeedOrder(User customer, Long addressId, String paymentMethod, String note, List<OrderItemRequest> items) {
        try {
            CreateOrderRequest req = new CreateOrderRequest();
            req.setAddressId(addressId);
            req.setPaymentMethod(paymentMethod);
            req.setCustomerNote(note);
            req.setItems(items);
            orderService.createOrder(customer, req);
        } catch (Exception ignored) {
        }
    }
}
