package com.smartgrocery.backend.service;

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

import java.math.BigDecimal;
import java.util.List;

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

    private TransactionTemplate neo4jTransactionTemplate;

    @Autowired
    public void setNeo4jTransactionManager(
            @Qualifier("neo4jTransactionManager") PlatformTransactionManager neo4jTransactionManager
    ) {
        this.neo4jTransactionTemplate = new TransactionTemplate(neo4jTransactionManager);
    }

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
        if (categoryRepository.count() == 0) {
            Category veg = Category.builder().categoryCode("CAT_VEG").name("Rau củ").build();
            categoryRepository.save(veg);

            Product p1 = Product.builder().productCode("P_BROC").name("Bông cải xanh").category(veg).build();
            productRepository.save(p1);

            productVariantRepository.save(ProductVariant.builder()
                    .product(p1).sku("SKU_B001").variantName("Bông cải xanh 500g")
                    .netPrice(BigDecimal.valueOf(30000)).status("ACTIVE").unit("PACK").build());
            System.out.println(">> Seeded Catalog!");
        }

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
}
