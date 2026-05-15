package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.dto.AdminProductUpsertRequest;
import com.smartgrocery.backend.entity.Category;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.CategoryRepository;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.WarehouseRepository;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AdminProductServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductVariantRepository productVariantRepository;
    @Mock InventoryStockRepository inventoryStockRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock SupabaseStorageService supabaseStorageService;
    @Mock AuditService auditService;
    @Mock ProductNodeRepository productNodeRepository;
    @Mock com.smartgrocery.backend.repository.jpa.CartItemRepository cartItemRepository;
    @Mock com.smartgrocery.backend.repository.jpa.WishlistItemRepository wishlistItemRepository;
    @Mock NotificationService notificationService;

    private AdminProductService service;
    private User actor;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new AdminProductService(
                categoryRepository,
                productRepository,
                productVariantRepository,
                inventoryStockRepository,
                warehouseRepository,
                productNodeRepository,
                supabaseStorageService,
                auditService,
                new ObjectMapper(),
                cartItemRepository,
                wishlistItemRepository,
                notificationService
        );
        ReflectionTestUtils.setField(service, "maxImageBytes", 2L * 1024L * 1024L);

        actor = User.builder().id(99L).email("admin@smartgrocery.test").build();
        Category category = Category.builder().id(10L).categoryCode("CAT").name("Fruit").build();
        product = Product.builder()
                .id(1L)
                .productCode("P_APPLE")
                .name("Apple")
                .category(category)
                .status("ACTIVE")
                .isFeatured(false)
                .build();
    }

    @Test
    void setStatusAllowsActiveAndHiddenButRejectsDeleted() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProduct_IdInAndStatusNot(anyList(), eq("DELETED"))).thenReturn(List.of());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.setStatus(actor, 1L, "HIDDEN", "hide from storefront");

        assertThat(dto.getStatus()).isEqualTo("HIDDEN");
        verify(auditService).log(eq(actor), eq("PRODUCT_STATUS"), eq("PRODUCT"), eq(1L), eq("hide from storefront"), any(), any());

        assertThatThrownBy(() -> service.setStatus(actor, 1L, "DELETED", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("soft delete");
    }

    @Test
    void softDeleteAndRestoreUseDeletedThenHiddenStatuses() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProduct_IdInAndStatusNot(anyList(), eq("DELETED"))).thenReturn(List.of());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var deleted = service.softDelete(actor, 1L, "retire product");
        assertThat(deleted.getStatus()).isEqualTo("DELETED");
        verify(auditService).log(eq(actor), eq("PRODUCT_SOFT_DELETE"), eq("PRODUCT"), eq(1L), eq("retire product"), any(), any());

        var restored = service.restore(actor, 1L, "restore product");
        assertThat(restored.getStatus()).isEqualTo("HIDDEN");
        verify(auditService).log(eq(actor), eq("PRODUCT_RESTORE"), eq("PRODUCT"), eq(1L), eq("restore product"), any(), any());
    }

    @Test
    void createRejectsDuplicateSkuInPayload() {
        AdminProductUpsertRequest request = new AdminProductUpsertRequest();
        request.setProductCode("P_NEW");
        request.setName("New Product");
        request.setCategoryId(10L);
        request.setVariantsJson("""
                [
                  {"sku":"SKU-1","netPrice":1000,"stock":5},
                  {"sku":"SKU-1","netPrice":1200,"stock":3}
                ]
                """);

        when(categoryRepository.findById(10L)).thenReturn(Optional.of(Category.builder().id(10L).name("Fruit").build()));
        when(productRepository.findByProductCode("P_NEW")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(actor, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate SKU");
    }

    @Test
    void exportExcelContainsExpectedHeaderAndVariantData() throws Exception {
        ProductVariant variant = ProductVariant.builder()
                .id(20L)
                .product(product)
                .sku("SKU-APPLE-RED")
                .color("Red")
                .size("1kg")
                .unit("bag")
                .netPrice(BigDecimal.valueOf(45000))
                .status("ACTIVE")
                .build();

        when(productRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(product));
        when(productVariantRepository.findByProduct_IdInAndStatusNot(anyList(), eq("DELETED"))).thenReturn(List.of(variant));
        when(inventoryStockRepository.sumAvailableByVariantIds(anyList())).thenReturn(List.of(new InventoryStockRepository.VariantStockSum() {
            @Override public Long getVariantId() { return 20L; }
            @Override public Long getTotalAvailable() { return 12L; }
        }));

        byte[] bytes = service.exportExcel(actor, null, null, null, null);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Products");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("ID");
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo("SKU-APPLE-RED");
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("Red");
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("1kg");
            assertThat(sheet.getRow(1).getCell(11).getNumericCellValue()).isEqualTo(12);
        }
        verify(auditService).log(eq(actor), eq("PRODUCT_EXPORT"), eq("PRODUCT"), eq(0L), eq("Export products"), any(), any());
    }

    @Test
    void searchMapsOneThousandRecordsWithinTwoSeconds() {
        List<Product> products = java.util.stream.LongStream.rangeClosed(1, 1000)
                .mapToObj(id -> Product.builder()
                        .id(id)
                        .productCode("P_" + id)
                        .name("Product " + id)
                        .category(Category.builder().id(10L).name("Fruit").build())
                        .status("ACTIVE")
                        .isFeatured(false)
                        .build())
                .toList();
        when(productRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(products, PageRequest.of(0, 1000), 1000));
        when(productVariantRepository.findByProduct_IdInAndStatusNot(anyList(), eq("DELETED"))).thenReturn(List.of());

        long started = System.nanoTime();
        var page = service.search(null, null, "ACTIVE", null, PageRequest.of(0, 1000));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(page.getTotalElements()).isEqualTo(1000);
        assertThat(elapsedMillis).isLessThan(2000);
    }
}
