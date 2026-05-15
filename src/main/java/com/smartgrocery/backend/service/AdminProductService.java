package com.smartgrocery.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.dto.AdminProductSummaryDto;
import com.smartgrocery.backend.dto.AdminProductUpsertRequest;
import com.smartgrocery.backend.dto.AdminProductVariantRequest;
import com.smartgrocery.backend.dto.BrandDto;
import com.smartgrocery.backend.dto.CategoryDto;
import com.smartgrocery.backend.dto.ProductDto;
import com.smartgrocery.backend.dto.ProductVariantDto;
import com.smartgrocery.backend.entity.Category;
import com.smartgrocery.backend.entity.InventoryStock;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.Warehouse;
import com.smartgrocery.backend.repository.jpa.CategoryRepository;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import com.smartgrocery.backend.repository.jpa.WarehouseRepository;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import com.smartgrocery.backend.entity.graph.ProductNode;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_HIDDEN = "HIDDEN";
    private static final String STATUS_DELETED = "DELETED";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductNodeRepository productNodeRepository;
    private final SupabaseStorageService supabaseStorageService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${app.upload.products-max-bytes:2097152}")
    private long maxImageBytes;

    @Transactional(value = "transactionManager", readOnly = true)
    public Page<ProductDto> search(String search, Long categoryId, String status, Pageable pageable) {
        Pageable safePageable = PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), 100),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "updatedAt")
        );
        log.info("Admin product search search={} categoryId={} status={} page={} size={}",
                search, categoryId, status, safePageable.getPageNumber(), safePageable.getPageSize());
        Page<Product> page = productRepository.findAll(productSpec(search, categoryId, status), safePageable);
        return toDtoPage(page);
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public AdminProductSummaryDto getSummary() {
        long total = productRepository.countByStatusNot(STATUS_DELETED);
        long active = productRepository.countByStatus(STATUS_ACTIVE);
        long hidden = productRepository.countByStatus(STATUS_HIDDEN);
        long deleted = productRepository.countByStatus(STATUS_DELETED);
        return AdminProductSummaryDto.builder()
                .totalCount(total)
                .activeCount(active)
                .hiddenCount(hidden)
                .deletedCount(deleted)
                .build();
    }

    @Transactional("transactionManager")
    public String cleanupProducts(User actor) {
        requireActor(actor);
        log.info("Admin {} initiated product cleanup", actor.getEmail());

        // 1. Activate all HIDDEN products
        int activated = productRepository.bulkActivateHidden();

        // 2. Hard delete all DELETED products and their variants
        List<Product> toDelete = productRepository.findByStatus(STATUS_DELETED);
        int deletedCount = toDelete.size();
        for (Product p : toDelete) {
            // Delete variants first (due to FK)
            List<ProductVariant> variants = productVariantRepository.findByProduct_Id(p.getId());
            productVariantRepository.deleteAll(variants);
            
            // Delete from Neo4j
            try {
                productNodeRepository.deleteById(p.getId());
            } catch (Exception e) {
                log.warn("Failed to delete product {} from Neo4j during cleanup", p.getId());
            }

            // Finally delete product
            productRepository.delete(p);
        }

        return String.format("Dọn dẹp thành công: Đã khôi phục %d sản phẩm ẩn và xóa vĩnh viễn %d sản phẩm.", activated, deletedCount);
    }

    @Transactional("transactionManager")
    public ProductDto create(User actor, AdminProductUpsertRequest req) {
        requireActor(actor);
        List<AdminProductVariantRequest> variantRequests = parseVariants(req, true);
        validateImage(req.getImage());
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category does not exist"));
        productRepository.findByProductCode(req.getProductCode().trim()).ifPresent(p -> {
            throw new IllegalArgumentException("Product code already exists");
        });
        validateUniqueVariantInput(variantRequests, null);

        String imagePath = req.getImage() != null && !req.getImage().isEmpty()
                ? supabaseStorageService.upload(req.getImage(), "products")
                : null;

        Product product = Product.builder()
                .productCode(req.getProductCode().trim())
                .name(req.getName().trim())
                .category(category)
                .shortDescription(trimToNull(req.getShortDescription()))
                .description(trimToNull(req.getDescription()))
                .originCountry(trimToNull(req.getOriginCountry()))
                .status(normalizeProductStatus(req.getStatus(), STATUS_ACTIVE, true))
                .isFeatured(Boolean.TRUE.equals(req.getIsFeatured()))
                .image(imagePath)
                .build();
        Product saved = productRepository.save(product);
        upsertVariants(saved, variantRequests);
        syncToNeo4j(saved);

        ProductDto result = getAdminProductById(saved.getId());
        auditService.log(actor, "PRODUCT_CREATE", "PRODUCT", saved.getId(), "Create product", null, snapshot(result));
        log.info("Admin product created productId={} actorId={}", saved.getId(), actor.getId());
        return result;
    }

    @Transactional("transactionManager")
    public ProductDto update(User actor, Long productId, AdminProductUpsertRequest req) {
        requireActor(actor);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        JsonNode before = snapshot(getAdminProductById(productId));
        List<AdminProductVariantRequest> variantRequests = parseVariants(req, false);
        validateImage(req.getImage());
        validateUniqueVariantInput(variantRequests, productId);

        if (req.getProductCode() != null && !req.getProductCode().isBlank() && !req.getProductCode().trim().equals(product.getProductCode())) {
            productRepository.findByProductCode(req.getProductCode().trim()).ifPresent(existing -> {
                throw new IllegalArgumentException("Product code already exists");
            });
            product.setProductCode(req.getProductCode().trim());
        }
        if (req.getName() != null && !req.getName().isBlank()) product.setName(req.getName().trim());
        if (req.getShortDescription() != null) product.setShortDescription(trimToNull(req.getShortDescription()));
        if (req.getDescription() != null) product.setDescription(trimToNull(req.getDescription()));
        if (req.getOriginCountry() != null) product.setOriginCountry(trimToNull(req.getOriginCountry()));
        if (req.getStatus() != null) product.setStatus(normalizeProductStatus(req.getStatus(), product.getStatus(), true));
        if (req.getIsFeatured() != null) product.setIsFeatured(req.getIsFeatured());
        if (req.getCategoryId() != null) {
            Category category = categoryRepository.findById(req.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category does not exist"));
            product.setCategory(category);
        }
        if (req.getImage() != null && !req.getImage().isEmpty()) {
            product.setImage(supabaseStorageService.upload(req.getImage(), "products"));
        }

        productRepository.save(product);
        upsertVariants(product, variantRequests);
        syncToNeo4j(product);

        ProductDto result = getAdminProductById(productId);
        auditService.log(actor, "PRODUCT_UPDATE", "PRODUCT", productId, "Update product", before, snapshot(result));
        log.info("Admin product updated productId={} actorId={}", productId, actor.getId());
        return result;
    }

    @Transactional("transactionManager")
    public ProductDto updateImage(User actor, Long productId, MultipartFile image) {
        requireActor(actor);
        validateImage(image);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        JsonNode before = snapshot(getAdminProductById(productId));
        product.setImage(supabaseStorageService.upload(image, "products"));
        productRepository.save(product);
        ProductDto result = getAdminProductById(productId);
        auditService.log(actor, "PRODUCT_IMAGE_UPDATE", "PRODUCT", productId, "Update product image", before, snapshot(result));
        return result;
    }

    @Transactional("transactionManager")
    public ProductDto setStatus(User actor, Long productId, String status, String reason) {
        requireActor(actor);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        String next = normalizeProductStatus(status, null, true);
        if (STATUS_DELETED.equals(next)) {
            throw new IllegalArgumentException("Use soft delete endpoint for DELETED status");
        }
        JsonNode before = snapshot(getAdminProductById(productId));
        product.setStatus(next);
        productRepository.save(product);
        syncToNeo4j(product);
        ProductDto result = getAdminProductById(productId);
        auditService.log(actor, "PRODUCT_STATUS", "PRODUCT", productId, reasonOrDefault(reason, "Change product status"), before, snapshot(result));
        log.info("Admin product status changed productId={} status={} actorId={}", productId, next, actor.getId());
        return result;
    }

    @Transactional("transactionManager")
    public ProductDto softDelete(User actor, Long productId, String reason) {
        requireActor(actor);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        JsonNode before = snapshot(getAdminProductById(productId));
        product.setStatus(STATUS_DELETED);
        productRepository.save(product);
        syncToNeo4j(product);
        ProductDto result = getAdminProductById(productId);
        auditService.log(actor, "PRODUCT_SOFT_DELETE", "PRODUCT", productId, reasonOrDefault(reason, "Soft delete product"), before, snapshot(result));
        log.info("Admin product soft deleted productId={} actorId={}", productId, actor.getId());
        return result;
    }

    @Transactional("transactionManager")
    public ProductDto restore(User actor, Long productId, String reason) {
        requireActor(actor);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        JsonNode before = snapshot(getAdminProductById(productId));
        product.setStatus(STATUS_HIDDEN);
        productRepository.save(product);
        syncToNeo4j(product);
        ProductDto result = getAdminProductById(productId);
        auditService.log(actor, "PRODUCT_RESTORE", "PRODUCT", productId, reasonOrDefault(reason, "Restore product"), before, snapshot(result));
        log.info("Admin product restored productId={} actorId={}", productId, actor.getId());
        return result;
    }

    @Transactional("transactionManager")
    public byte[] exportExcel(User actor, String search, Long categoryId, String status) {
        requireActor(actor);
        List<Product> products = productRepository.findAll(
                productSpec(search, categoryId, status),
                Sort.by(Sort.Direction.DESC, "updatedAt")
        );
        List<ProductDto> rows = toDtos(products);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Products");
            Row header = sheet.createRow(0);
            String[] cols = {"ID", "code", "name", "category", "status", "featured", "variant SKU", "color", "size", "unit", "price", "stock", "updatedAt"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            int rowIndex = 1;
            for (ProductDto product : rows) {
                List<ProductVariantDto> variants = product.getVariants() == null || product.getVariants().isEmpty()
                        ? List.of(ProductVariantDto.builder().build())
                        : product.getVariants();
                for (ProductVariantDto variant : variants) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(product.getId() != null ? product.getId() : 0);
                    row.createCell(1).setCellValue(nullToEmpty(product.getProductCode()));
                    row.createCell(2).setCellValue(nullToEmpty(product.getName()));
                    row.createCell(3).setCellValue(product.getCategory() != null ? nullToEmpty(product.getCategory().getName()) : "");
                    row.createCell(4).setCellValue(nullToEmpty(product.getStatus()));
                    row.createCell(5).setCellValue(Boolean.TRUE.equals(product.getIsFeatured()));
                    row.createCell(6).setCellValue(nullToEmpty(variant.getSku()));
                    row.createCell(7).setCellValue(nullToEmpty(variant.getColor()));
                    row.createCell(8).setCellValue(nullToEmpty(variant.getSize()));
                    row.createCell(9).setCellValue(nullToEmpty(variant.getUnit()));
                    row.createCell(10).setCellValue(variant.getNetPrice() != null ? variant.getNetPrice().doubleValue() : 0);
                    row.createCell(11).setCellValue(variant.getStock() != null ? variant.getStock() : 0);
                    row.createCell(12).setCellValue(product.getUpdatedAt() != null ? product.getUpdatedAt().toString() : "");
                }
            }
            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            auditService.log(actor, "PRODUCT_EXPORT", "PRODUCT", 0L, "Export products", null,
                    objectMapper.valueToTree(Map.of(
                            "rows", rowIndex - 1,
                            "search", search != null ? search : "",
                            "categoryId", categoryId != null ? categoryId : 0,
                            "status", status != null ? status : ""
                    )));
            log.info("Admin product export rows={} actorId={}", rowIndex - 1, actor.getId());
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Could not export products", e);
        }
    }

    private Specification<Product> productSpec(String search, Long categoryId, String status) {
        return (root, query, cb) -> {
            if (query != null) query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), normalizeProductStatus(status, null, true)));
            } else {
                predicates.add(cb.notEqual(cb.upper(root.get("status")), STATUS_DELETED));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("productCode")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Page<ProductDto> toDtoPage(Page<Product> page) {
        List<ProductDto> dtos = toDtos(page.getContent());
        Map<Long, ProductDto> byId = dtos.stream().collect(Collectors.toMap(ProductDto::getId, p -> p));
        return page.map(p -> byId.get(p.getId()));
    }

    private ProductDto getAdminProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        return toDtos(List.of(product)).get(0);
    }

    private List<ProductDto> toDtos(List<Product> products) {
        if (products.isEmpty()) return List.of();
        List<Long> productIds = products.stream().map(Product::getId).toList();
        List<ProductVariant> variants = productVariantRepository.findByProduct_IdInAndStatusNot(productIds, STATUS_DELETED);
        Map<Long, List<ProductVariant>> variantsByProductId = variants.stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId()));
        List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<Long, Integer> stockByVariantId = variantIds.isEmpty()
                ? Map.of()
                : inventoryStockRepository.sumAvailableByVariantIds(variantIds).stream()
                        .collect(Collectors.toMap(
                                InventoryStockRepository.VariantStockSum::getVariantId,
                                row -> row.getTotalAvailable().intValue(),
                                (a, b) -> a
                        ));
        return products.stream().map(p -> toDto(p, variantsByProductId.getOrDefault(p.getId(), List.of()), stockByVariantId)).toList();
    }

    private ProductDto toDto(Product product, List<ProductVariant> variants, Map<Long, Integer> stockByVariantId) {
        return ProductDto.builder()
                .id(product.getId())
                .productCode(product.getProductCode())
                .name(product.getName())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .image(product.getImage())
                .originCountry(product.getOriginCountry())
                .status(product.getStatus())
                .isFeatured(product.getIsFeatured())
                .purchaseCount(0L)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .category(product.getCategory() != null ? CategoryDto.builder()
                        .id(product.getCategory().getId())
                        .categoryCode(product.getCategory().getCategoryCode())
                        .name(product.getCategory().getName())
                        .description(product.getCategory().getDescription())
                        .build() : null)
                .brand(product.getBrand() != null ? BrandDto.builder()
                        .id(product.getBrand().getId())
                        .name(product.getBrand().getName())
                        .description(product.getBrand().getDescription())
                        .status(product.getBrand().getStatus())
                        .build() : null)
                .variants(variants.stream().map(v -> ProductVariantDto.builder()
                        .id(v.getId())
                        .sku(v.getSku())
                        .barcode(v.getBarcode())
                        .variantName(v.getVariantName())
                        .color(v.getColor())
                        .size(v.getSize())
                        .unit(v.getUnit())
                        .packageSize(v.getPackageSize())
                        .weightGram(v.getWeightGram())
                        .netPrice(v.getNetPrice())
                        .compareAtPrice(v.getCompareAtPrice())
                        .vatPercent(v.getVatPercent())
                        .status(v.getStatus())
                        .stock(stockByVariantId.getOrDefault(v.getId(), 0))
                        .build()).toList())
                .build();
    }

    private void upsertVariants(Product product, List<AdminProductVariantRequest> requests) {
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("At least one variant is required");
        }
        Map<Long, ProductVariant> existingById = productVariantRepository.findByProduct_IdAndStatusNot(product.getId(), STATUS_DELETED)
                .stream()
                .filter(v -> v.getId() != null)
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));
        Set<Long> retainedIds = new HashSet<>();
        for (AdminProductVariantRequest request : requests) {
            ProductVariant variant = request.getId() != null ? existingById.get(request.getId()) : null;
            if (request.getId() != null && variant == null) {
                throw new IllegalArgumentException("Variant does not exist on product");
            }
            if (variant == null) {
                variant = ProductVariant.builder().product(product).build();
            }
            applyVariantFields(variant, request);
            ProductVariant saved = productVariantRepository.save(variant);
            retainedIds.add(saved.getId());
            updateStock(saved, request.getStock());
        }
        existingById.values().stream()
                .filter(v -> !retainedIds.contains(v.getId()))
                .forEach(v -> {
                    v.setStatus(STATUS_DELETED);
                    productVariantRepository.save(v);
                });
    }

    private void applyVariantFields(ProductVariant variant, AdminProductVariantRequest request) {
        variant.setSku(request.getSku().trim());
        variant.setBarcode(trimToNull(request.getBarcode()));
        variant.setVariantName(trimToNull(request.getVariantName()));
        variant.setColor(trimToNull(request.getColor()));
        variant.setSize(trimToNull(request.getSize()));
        variant.setUnit(trimToNull(request.getUnit()) != null ? request.getUnit().trim() : "unit");
        variant.setNetPrice(request.getNetPrice() != null ? request.getNetPrice() : BigDecimal.ZERO);
        variant.setStatus(normalizeVariantStatus(request.getStatus()));
    }

    private void updateStock(ProductVariant variant, Integer stock) {
        Warehouse warehouse = warehouseRepository.findAll().stream().findFirst()
                .orElseGet(() -> warehouseRepository.save(Warehouse.builder().code("WH_MAIN").name("Kho Trung Tam").location("TP. Thu Duc").build()));
        int available = stock != null ? Math.max(0, stock) : 0;
        inventoryStockRepository.findByWarehouseIdAndVariantId(warehouse.getId(), variant.getId()).ifPresentOrElse(
                s -> {
                    s.setAvailableQuantity(available);
                    inventoryStockRepository.save(s);
                },
                () -> inventoryStockRepository.save(InventoryStock.builder()
                        .warehouse(warehouse)
                        .variant(variant)
                        .availableQuantity(available)
                        .reservedQuantity(0)
                        .build())
        );
    }

    private List<AdminProductVariantRequest> parseVariants(AdminProductUpsertRequest req, boolean requireLegacyFallback) {
        List<AdminProductVariantRequest> variants = new ArrayList<>();
        if (req.getVariantsJson() != null && !req.getVariantsJson().isBlank()) {
            try {
                variants = objectMapper.readValue(req.getVariantsJson(), new TypeReference<List<AdminProductVariantRequest>>() {});
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid variantsJson");
            }
        }
        if (variants.isEmpty() && (requireLegacyFallback || req.getSku() != null || req.getNetPrice() != null)) {
            AdminProductVariantRequest v = new AdminProductVariantRequest();
            v.setSku(req.getSku());
            v.setBarcode(req.getBarcode());
            v.setVariantName(req.getVariantName());
            v.setColor(req.getColor());
            v.setSize(req.getSize());
            v.setUnit(req.getUnit());
            v.setNetPrice(req.getNetPrice());
            v.setStock(req.getStock());
            v.setStatus(STATUS_ACTIVE);
            variants.add(v);
        }
        validateVariants(variants);
        return variants;
    }

    private void validateVariants(List<AdminProductVariantRequest> variants) {
        if (variants.isEmpty()) throw new IllegalArgumentException("At least one variant is required");
        for (AdminProductVariantRequest v : variants) {
            if (v.getSku() == null || v.getSku().isBlank()) throw new IllegalArgumentException("SKU is required");
            if (v.getNetPrice() == null || v.getNetPrice().compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Price must be greater than or equal to 0");
            if (v.getStock() == null || v.getStock() < 0) throw new IllegalArgumentException("Stock must be greater than or equal to 0");
            normalizeVariantStatus(v.getStatus());
        }
    }

    private void validateUniqueVariantInput(List<AdminProductVariantRequest> requests, Long productId) {
        Set<String> payloadSkus = new HashSet<>();
        for (AdminProductVariantRequest request : requests) {
            String sku = request.getSku().trim().toLowerCase(Locale.ROOT);
            if (!payloadSkus.add(sku)) {
                throw new IllegalArgumentException("Duplicate SKU in variants");
            }
            productVariantRepository.findBySku(request.getSku().trim()).ifPresent(existing -> {
                boolean sameProduct = productId != null && existing.getProduct() != null && productId.equals(existing.getProduct().getId());
                boolean sameVariant = request.getId() != null && request.getId().equals(existing.getId());
                if (!sameProduct || !sameVariant) {
                    throw new IllegalArgumentException("SKU already exists");
                }
            });
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) return;
        if (image.getSize() > maxImageBytes) {
            throw new IllegalArgumentException("Image must be less than or equal to 2MB");
        }
        String type = image.getContentType();
        if (!Set.of("image/jpeg", "image/png", "image/webp").contains(type)) {
            throw new IllegalArgumentException("Image must be jpg, png or webp");
        }
    }

    private String normalizeProductStatus(String raw, String fallback, boolean allowDeleted) {
        if (raw == null || raw.isBlank()) return fallback;
        String status = raw.trim().toUpperCase(Locale.ROOT);
        if (STATUS_ACTIVE.equals(status) || STATUS_HIDDEN.equals(status) || (allowDeleted && STATUS_DELETED.equals(status))) {
            return status;
        }
        throw new IllegalArgumentException("Invalid product status");
    }

    private String normalizeVariantStatus(String raw) {
        if (raw == null || raw.isBlank()) return STATUS_ACTIVE;
        String status = raw.trim().toUpperCase(Locale.ROOT);
        if (STATUS_ACTIVE.equals(status) || STATUS_HIDDEN.equals(status) || STATUS_DELETED.equals(status)) {
            return status;
        }
        throw new IllegalArgumentException("Invalid variant status");
    }

    private void requireActor(User actor) {
        if (actor == null) throw new IllegalArgumentException("Missing actor");
    }

    private JsonNode snapshot(Object value) {
        return objectMapper.valueToTree(value);
    }

    private String reasonOrDefault(String reason, String fallback) {
        return reason != null && !reason.isBlank() ? reason.trim() : fallback;
    }

    private String trimToNull(String v) {
        if (v == null) return null;
        String trimmed = v.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void syncToNeo4j(Product product) {
        try {
            if (STATUS_DELETED.equals(product.getStatus())) {
                productNodeRepository.deleteById(product.getId());
                log.info("AI Sync: Deleted ProductNode ID: {}", product.getId());
                return;
            }
            ProductNode node = productNodeRepository.findById(product.getId())
                    .orElse(ProductNode.builder().productId(product.getId()).build());
            
            node.setName(product.getName());
            node.setDescription(product.getDescription());
            
            // Get price from the first active variant
            List<ProductVariant> variants = productVariantRepository.findByProduct_IdAndStatus(product.getId(), STATUS_ACTIVE);
            if (variants.isEmpty()) {
                variants = productVariantRepository.findByProduct_Id(product.getId());
            }
            double price = 0.0;
            if (!variants.isEmpty()) {
                BigDecimal netPrice = variants.get(0).getNetPrice();
                if (netPrice != null) {
                    price = netPrice.doubleValue();
                }
            }
            node.setPrice(price);
            
            productNodeRepository.save(node);
            log.info("AI Sync: Updated ProductNode ID: {} - {}", product.getId(), product.getName());
        } catch (Exception e) {
            log.warn("AI Sync failed for product {}: {}", product.getId(), e.getMessage());
        }
    }
}
