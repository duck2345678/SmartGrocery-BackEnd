package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.BrandDto;
import com.smartgrocery.backend.dto.CategoryDto;
import com.smartgrocery.backend.dto.ProductDto;
import com.smartgrocery.backend.dto.ProductVariantDto;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.repository.InventoryStockRepository;
import com.smartgrocery.backend.repository.OrderItemRepository;
import com.smartgrocery.backend.repository.ProductRepository;
import com.smartgrocery.backend.repository.ProductVariantRepository;
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

        if (hasSearch && hasCategory) {
            page = productRepository.findByNameContainingIgnoreCaseAndCategoryId(trimmedSearch, categoryId, pageable);
        } else if (hasSearch) {
            page = productRepository.findByNameContainingIgnoreCase(trimmedSearch, pageable);
        } else if (hasCategory) {
            page = productRepository.findByCategoryId(categoryId, pageable);
        } else {
            page = productRepository.findAll(pageable);
        }

        List<Long> productIds = page.getContent().stream().map(Product::getId).toList();
        Map<Long, Long> purchaseCountByProductId = getPurchaseCountByProductIds(productIds);
        return page.map(product -> mapToDto(product, purchaseCountByProductId.getOrDefault(product.getId(), 0L)));
    }

    public ProductDto getProductById(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new RuntimeException("Product not found"));
        Long purchaseCount = getPurchaseCountByProductIds(List.of(product.getId())).getOrDefault(product.getId(), 0L);
        return mapToDto(product, purchaseCount);
    }

    public ProductDto getProductByCode(String productCode) {
        Product product = productRepository.findByProductCode(productCode).orElseThrow(() -> new RuntimeException("Product not found"));
        Long purchaseCount = getPurchaseCountByProductIds(List.of(product.getId())).getOrDefault(product.getId(), 0L);
        return mapToDto(product, purchaseCount);
    }

    private ProductDto mapToDto(Product product, Long purchaseCount) {
        List<ProductVariant> variants = productVariantRepository.findByProduct_Id(product.getId());

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
                        .unit(v.getUnit())
                        .packageSize(v.getPackageSize())
                        .weightGram(v.getWeightGram())
                        .aisleLocation(v.getAisleLocation())
                        .netPrice(v.getNetPrice())
                        .compareAtPrice(v.getCompareAtPrice())
                        .vatPercent(v.getVatPercent())
                        .status(v.getStatus())
                        .stock(inventoryStockRepository.sumAvailableByVariantId(v.getId()) != null ? inventoryStockRepository.sumAvailableByVariantId(v.getId()).intValue() : 0)
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
