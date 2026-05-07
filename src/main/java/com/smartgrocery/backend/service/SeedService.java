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

    @Autowired
    private ShiftScheduleRepository shiftScheduleRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

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

        User staff = userRepository.findByEmail("staff.p0@smartgrocery.com").orElse(null);
        if (staff == null) {
            staff = User.builder()
                    .email("staff.p0@smartgrocery.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .fullName("P0 Staff")
                    .phone("0912345678")
                    .role(staffRole)
                    .status("ACTIVE")
                    .build();
            userRepository.save(staff);
            System.out.println(">> Seeded P0 Staff: staff.p0@smartgrocery.com / password123");
        } else if (staffRole != null && !staffRole.equals(staff.getRole())) {
            staff.setRole(staffRole);
            staff.setStatus("ACTIVE");
            userRepository.save(staff);
            System.out.println(">> Updated P0 Staff role to STAFF");
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
                new SeedProduct("P_BROC", "Bông cải xanh", veg, "SKU_B001", "BAR_B001", "Bông cải xanh 500g", "PACK", BigDecimal.valueOf(30000), 200),
                new SeedProduct("P_CARROT", "Cà rốt Đà Lạt", veg, "SKU_V002", "BAR_V002", "Cà rốt 1kg", "BAG", BigDecimal.valueOf(28000), 200),
                new SeedProduct("P_TOMATO", "Cà chua bi", veg, "SKU_V003", "BAR_V003", "Cà chua bi 500g", "BOX", BigDecimal.valueOf(25000), 200),
                new SeedProduct("P_APPLE", "Táo đỏ Mỹ", fruit, "SKU_F001", "BAR_F001", "Táo đỏ 1kg", "BAG", BigDecimal.valueOf(68000), 200),
                new SeedProduct("P_BANANA", "Chuối già Nam Mỹ", fruit, "SKU_F002", "BAR_F002", "Chuối 1 nải", "BUNCH", BigDecimal.valueOf(22000), 200),
                new SeedProduct("P_ORANGE", "Cam sành", fruit, "SKU_F003", "BAR_F003", "Cam 1kg", "BAG", BigDecimal.valueOf(42000), 200),
                new SeedProduct("P_MILK", "Sữa tươi không đường", dairy, "SKU_D001", "BAR_D001", "Hộp 1L", "BOX", BigDecimal.valueOf(33000), 200),
                new SeedProduct("P_EGGS", "Trứng gà", dairy, "SKU_D002", "BAR_D002", "Vỉ 10 trứng", "TRAY", BigDecimal.valueOf(32000), 200),
                new SeedProduct("P_PORK", "Thịt heo ba rọi", meat, "SKU_M001", "BAR_M001", "500g", "PACK", BigDecimal.valueOf(75000), 200),
                new SeedProduct("P_RICE", "Gạo ST25", staple, "SKU_S001", "BAR_S001", "Túi 5kg", "BAG", BigDecimal.valueOf(165000), 200)
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
        try {
            seedFulfillmentOrdersIfNeeded(longNameVariant.getId());
        } catch (Exception e) {
            System.err.println(">> Warning: Failed to seed fulfillment orders: " + e.getMessage());
        }

        // 6. Graph (Neo4j)
        try {
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
        } catch (Exception e) {
            System.err.println(">> Warning: Neo4j synchronization failed (database may not be running): " + e.getMessage());
        }

        // 7. Seed Staff Shift Schedule and Attendance Records
        final User finalStaff = staff;
        if (finalStaff != null) {
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate dayMinus3 = today.minusDays(3);
            
            // Hàm helper để tạo lịch làm việc
            java.util.function.BiConsumer<java.time.LocalDate, String> createShift = (date, type) -> {
                if (shiftScheduleRepository.findByUser_IdAndWorkDate(finalStaff.getId(), date).isEmpty()) {
                    ShiftSchedule.ShiftScheduleBuilder builder = ShiftSchedule.builder()
                            .user(finalStaff)
                            .workDate(date)
                            .shiftType(type);
                    if ("G".equals(type)) {
                        if (date.equals(today)) {
                            builder.selectedBlocks("1,4");
                        } else if (date.equals(today.plusDays(3))) {
                            builder.selectedBlocks("1,3");
                        } else if (date.equals(dayMinus3)) {
                            builder.selectedBlocks("2,4");
                        } else {
                            builder.selectedBlocks("1,4");
                        }
                    }
                    shiftScheduleRepository.save(builder.build());
                }
            };

            // Hàm helper để tạo record chấm công giả lập
            java.util.function.Consumer<AttendanceRecord> createRecord = (record) -> {
                if (attendanceRecordRepository.findByUser_IdAndWorkDateAndBlockNumber(
                        record.getUser().getId(), record.getWorkDate(), record.getBlockNumber()).isEmpty()) {
                    attendanceRecordRepository.save(record);
                }
            };

            // 7.1 Lịch tương lai (SCHEDULED)
            createShift.accept(today.plusDays(1), "S");
            createShift.accept(today.plusDays(2), "C");
            createShift.accept(today.plusDays(3), "G");
            createShift.accept(today.plusDays(4), "OFF");

            // 7.2 Lịch hôm nay (S) - Ca sáng theo yêu cầu người dùng
            ShiftSchedule todayShift = shiftScheduleRepository.findByUser_IdAndWorkDate(finalStaff.getId(), today).orElse(null);
            if (todayShift == null) {
                shiftScheduleRepository.save(ShiftSchedule.builder()
                        .user(finalStaff)
                        .workDate(today)
                        .shiftType("S")
                        .build());
            } else if (!"S".equals(todayShift.getShiftType())) {
                todayShift.setShiftType("S");
                todayShift.setSelectedBlocks(null); // Clear split blocks if any
                shiftScheduleRepository.save(todayShift);
            }

            // 7.3 Lịch quá khứ đa dạng
            java.time.LocalDate dayMinus1 = today.minusDays(1);
            java.time.LocalDate dayMinus2 = today.minusDays(2);
            java.time.LocalDate dayMinus4 = today.minusDays(4);
            java.time.LocalDate dayMinus5 = today.minusDays(5);

            createShift.accept(dayMinus1, "S"); // Hôm qua: Đi đúng giờ, về đúng giờ (Xanh)
            createShift.accept(dayMinus2, "C"); // Đi trễ (Cam)
            createShift.accept(dayMinus3, "G"); // Ca Gãy: Thiếu 1 block (Cam)
            createShift.accept(dayMinus4, "S"); // Vắng mặt (Đỏ - Có lịch nhưng ko checkin)
            createShift.accept(dayMinus5, "OFF"); // Nghỉ (Trắng)

            // Tạo các bản ghi chấm công (AttendanceRecord) cho các ngày quá khứ
            // Day -1: Đúng giờ
            createRecord.accept(AttendanceRecord.builder()
                    .user(finalStaff).workDate(dayMinus1).shiftType("S").blockNumber(1)
                    .checkInAt(dayMinus1.atTime(6, 25))
                    .checkOutAt(dayMinus1.atTime(14, 35))
                    .checkInStatus("ON_TIME").checkOutStatus("ON_TIME")
                    .build());

            // Day -2: LATE
            createRecord.accept(AttendanceRecord.builder()
                    .user(finalStaff).workDate(dayMinus2).shiftType("C").blockNumber(1)
                    .checkInAt(dayMinus2.atTime(14, 50)) // Trễ so với 14:30
                    .checkOutAt(dayMinus2.atTime(22, 35))
                    .checkInStatus("LATE").checkOutStatus("ON_TIME")
                    .build());

            // Day -3: Ca Gãy, có block 1, ko có block 2 -> Incomplete -> Cam
            createRecord.accept(AttendanceRecord.builder()
                    .user(finalStaff).workDate(dayMinus3).shiftType("G").blockNumber(1)
                    .checkInAt(dayMinus3.atTime(6, 20))
                    .checkOutAt(dayMinus3.atTime(10, 30))
                    .checkInStatus("ON_TIME").checkOutStatus("ON_TIME")
                    .build());
            // (Không tạo block 2)

            // Day -4: Có lịch nhưng ko tạo AttendanceRecord -> ABSENT -> Đỏ

            System.out.println(">> Seeded diverse ShiftSchedules and AttendanceRecords for P0 Staff!");
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private void seedFulfillmentOrdersIfNeeded(Long longNameVariantId) {
        // Skip if we already have test orders
        if (orderRepository.count() >= 2) {
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

        if (baseVariants.size() < 3) return;

        // Create only one simple test order to avoid inventory conflicts
        createSeedOrder(customer, addressId, "COD", "SEED_UI_TEST_ORDER_1", List.of(
                OrderItemRequest.builder().variantId(baseVariants.get(0).getId()).quantity(1).build(),
                OrderItemRequest.builder().variantId(baseVariants.get(1).getId()).quantity(1).build()
        ));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private void createSeedOrder(User customer, Long addressId, String paymentMethod, String note, List<OrderItemRequest> items) {
        try {
            CreateOrderRequest req = new CreateOrderRequest();
            req.setAddressId(addressId);
            req.setPaymentMethod(paymentMethod);
            req.setCustomerNote(note);
            req.setItems(items);
            orderService.createOrder(customer, req);
        } catch (Exception e) {
            System.err.println(">> Failed to create seed order [" + note + "]: " + e.getMessage());
        }
    }
}
