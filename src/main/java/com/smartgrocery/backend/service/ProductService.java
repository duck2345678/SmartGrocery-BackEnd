package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.BrandDto;
import com.smartgrocery.backend.dto.CategoryDto;
import com.smartgrocery.backend.dto.ProductDto;
import com.smartgrocery.backend.dto.ProductVariantDto;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import com.smartgrocery.backend.repository.jpa.OrderItemRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager", readOnly = true)
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    public Page<ProductDto> getAllProducts(Pageable pageable) {
        return getProducts(null, null, pageable);
    }

    public Page<ProductDto> getProducts(String search, Long categoryId, Pageable pageable) {
        Page<Product> page;
        boolean hasSearch = search != null && !search.trim().isEmpty();
        boolean hasCategory = categoryId != null;
        String trimmedSearch = search != null ? search.trim() : "";

        if (hasSearch || hasCategory) {
            page = productRepository.findAll((root, query, cb) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
                
                // Base filter: must be ACTIVE
                predicates.add(cb.equal(cb.upper(root.get("status")), "ACTIVE"));

                if (hasSearch) {
                    String pattern = "%" + trimmedSearch.toLowerCase(java.util.Locale.ROOT) + "%";
                    
                    // Join with variants
                    jakarta.persistence.criteria.Join<Product, ProductVariant> variantsJoin = root.join("variants", jakarta.persistence.criteria.JoinType.LEFT);
                    
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("productCode")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("shortDescription")), pattern),
                        cb.like(cb.lower(root.get("category").get("name")), pattern),
                        cb.like(cb.lower(variantsJoin.get("variantName")), pattern),
                        cb.like(cb.lower(variantsJoin.get("sku")), pattern)
                    ));
                    query.distinct(true);
                }

                if (hasCategory) {
                    predicates.add(cb.equal(root.get("category").get("id"), categoryId));
                }

                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            }, pageable);
        } else {
            page = productRepository.findAll((root, query, cb) -> cb.equal(cb.upper(root.get("status")), "ACTIVE"), pageable);
        }

        List<Long> productIds = page.getContent().stream().map(Product::getId).toList();
        Map<Long, Long> purchaseCountByProductId = getPurchaseCountByProductIds(productIds);
        
        // 1. Bulk fetch all variants for these products
        List<ProductVariant> allVariants = productIds.isEmpty()
                ? List.of()
                : productVariantRepository.findByProduct_IdInAndStatus(productIds, "ACTIVE");
        Map<Long, List<ProductVariant>> variantsByProductId = allVariants.stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        // 2. Bulk fetch all inventory sums for these variants
        List<Long> variantIds = allVariants.stream().map(ProductVariant::getId).toList();
        Map<Long, Integer> stockByVariantId = Map.of();
        if (!variantIds.isEmpty()) {
            stockByVariantId = inventoryStockRepository.sumAvailableByVariantIds(variantIds).stream()
                    .collect(Collectors.toMap(
                            row -> row.getVariantId(),
                            row -> row.getTotalAvailable().intValue()
                    ));
        }

        final Map<Long, Integer> finalStockMap = stockByVariantId;
        return page.map(product -> {
            List<ProductVariant> variants = variantsByProductId.getOrDefault(product.getId(), List.of());
            Long pCount = purchaseCountByProductId.getOrDefault(product.getId(), 0L);
            return mapToDto(product, variants, pCount, finalStockMap);
        });
    }

    public ProductDto getProductById(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new RuntimeException("Product not found"));
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new RuntimeException("Product not found");
        }
        Long purchaseCount = getPurchaseCountByProductIds(List.of(product.getId())).getOrDefault(product.getId(), 0L);
        return mapToDto(product, purchaseCount);
    }

    public ProductDto getProductByCode(String productCode) {
        Product product = productRepository.findByProductCode(productCode).orElseThrow(() -> new RuntimeException("Product not found"));
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new RuntimeException("Product not found");
        }
        Long purchaseCount = getPurchaseCountByProductIds(List.of(product.getId())).getOrDefault(product.getId(), 0L);
        return mapToDto(product, purchaseCount);
    }

    private ProductDto mapToDto(Product product, Long purchaseCount) {
        List<ProductVariant> variants = productVariantRepository.findByProduct_IdAndStatus(product.getId(), "ACTIVE");
        List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<Long, Integer> stockByVariantId = Map.of();
        if (!variantIds.isEmpty()) {
            stockByVariantId = inventoryStockRepository.sumAvailableByVariantIds(variantIds).stream()
                    .collect(Collectors.toMap(
                            row -> row.getVariantId(),
                            row -> row.getTotalAvailable().intValue()
                    ));
        }
        return mapToDto(product, variants, purchaseCount, stockByVariantId);
    }

    private ProductDto mapToDto(Product product, List<ProductVariant> variants, Long purchaseCount, Map<Long, Integer> stockByVariantId) {
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
                .purchaseCount(purchaseCount != null ? purchaseCount : 0L)
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
                        .flashSaleEndsAt(v.getFlashSaleEndsAt())
                        .build()).collect(Collectors.toList()))
                .build();
    }

    private Map<Long, Long> getPurchaseCountByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return Map.of();
        return orderItemRepository.sumPurchasedQuantityByProductIds(productIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a
                ));
    }
}
