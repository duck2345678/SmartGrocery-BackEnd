package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AdminProductUpsertRequest;
import com.smartgrocery.backend.dto.ProductDto;
import com.smartgrocery.backend.entity.Category;
import com.smartgrocery.backend.entity.InventoryStock;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.Warehouse;
import com.smartgrocery.backend.repository.CategoryRepository;
import com.smartgrocery.backend.repository.InventoryStockRepository;
import com.smartgrocery.backend.repository.ProductRepository;
import com.smartgrocery.backend.repository.ProductVariantRepository;
import com.smartgrocery.backend.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminProductService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductImageStorageService productImageStorageService;

    @Autowired
    private ProductService productService;

    @Transactional("transactionManager")
    public ProductDto create(AdminProductUpsertRequest req) {
        if (req.getProductCode() == null || req.getProductCode().isBlank()) {
            throw new IllegalArgumentException("Thiếu productCode");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("Thiếu tên sản phẩm");
        }
        if (req.getCategoryId() == null) {
            throw new IllegalArgumentException("Thiếu categoryId");
        }
        if (req.getSku() == null || req.getSku().isBlank()) {
            throw new IllegalArgumentException("Thiếu SKU");
        }
        if (req.getNetPrice() == null) {
            throw new IllegalArgumentException("Thiếu giá");
        }

        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category không tồn tại"));

        productRepository.findByProductCode(req.getProductCode()).ifPresent(p -> {
            throw new IllegalArgumentException("Product code đã tồn tại");
        });
        productVariantRepository.findBySku(req.getSku()).ifPresent(v -> {
            throw new IllegalArgumentException("SKU đã tồn tại");
        });

        String imagePath = req.getImage() != null && !req.getImage().isEmpty()
                ? productImageStorageService.store(req.getImage())
                : null;

        Product product = Product.builder()
                .productCode(req.getProductCode())
                .name(req.getName())
                .category(category)
                .shortDescription(req.getShortDescription())
                .description(req.getDescription())
                .originCountry(req.getOriginCountry())
                .status(req.getStatus() != null ? req.getStatus() : "ACTIVE")
                .isFeatured(Boolean.TRUE.equals(req.getIsFeatured()))
                .image(imagePath)
                .build();
        product = productRepository.save(product);

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(req.getSku())
                .barcode(req.getBarcode())
                .variantName(req.getVariantName())
                .unit(req.getUnit() != null ? req.getUnit() : "unit")
                .netPrice(req.getNetPrice())
                .status("ACTIVE")
                .build();
        variant = productVariantRepository.save(variant);

        Warehouse warehouse = warehouseRepository.findAll().stream().findFirst()
                .orElseGet(() -> warehouseRepository.save(Warehouse.builder().code("WH_MAIN").name("Kho Trung Tâm").location("TP. Thủ Đức").build()));

        Integer available = req.getStock() != null ? Math.max(0, req.getStock()) : 0;
        inventoryStockRepository.save(InventoryStock.builder()
                .warehouse(warehouse)
                .variant(variant)
                .availableQuantity(available)
                .reservedQuantity(0)
                .build());

        return productService.getProductById(product.getId());
    }

    @Transactional("transactionManager")
    public ProductDto update(Long productId, AdminProductUpsertRequest req) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product không tồn tại"));

        if (req.getName() != null) product.setName(req.getName());
        if (req.getShortDescription() != null) product.setShortDescription(req.getShortDescription());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getOriginCountry() != null) product.setOriginCountry(req.getOriginCountry());
        if (req.getStatus() != null) product.setStatus(req.getStatus());
        if (req.getIsFeatured() != null) product.setIsFeatured(req.getIsFeatured());

        if (req.getCategoryId() != null) {
            Category category = categoryRepository.findById(req.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category không tồn tại"));
            product.setCategory(category);
        }

        if (req.getImage() != null && !req.getImage().isEmpty()) {
            product.setImage(productImageStorageService.store(req.getImage()));
        }

        productRepository.save(product);

        List<ProductVariant> variants = productVariantRepository.findByProduct_Id(productId);
        if (!variants.isEmpty()) {
            ProductVariant v = variants.get(0);
            if (req.getSku() != null && !req.getSku().isBlank() && !req.getSku().equals(v.getSku())) {
                productVariantRepository.findBySku(req.getSku()).ifPresent(existing -> {
                    throw new IllegalArgumentException("SKU đã tồn tại");
                });
                v.setSku(req.getSku());
            }
            if (req.getBarcode() != null) v.setBarcode(req.getBarcode());
            if (req.getVariantName() != null) v.setVariantName(req.getVariantName());
            if (req.getUnit() != null) v.setUnit(req.getUnit());
            if (req.getNetPrice() != null) v.setNetPrice(req.getNetPrice());
            productVariantRepository.save(v);

            if (req.getStock() != null) {
                Warehouse warehouse = warehouseRepository.findAll().stream().findFirst()
                        .orElseGet(() -> warehouseRepository.save(Warehouse.builder().code("WH_MAIN").name("Kho Trung Tâm").location("TP. Thủ Đức").build()));
                inventoryStockRepository.findByWarehouseIdAndVariantId(warehouse.getId(), v.getId()).ifPresentOrElse(
                        s -> {
                            s.setAvailableQuantity(Math.max(0, req.getStock()));
                            inventoryStockRepository.save(s);
                        },
                        () -> inventoryStockRepository.save(InventoryStock.builder()
                                .warehouse(warehouse)
                                .variant(v)
                                .availableQuantity(Math.max(0, req.getStock()))
                                .reservedQuantity(0)
                                .build())
                );
            }
        }

        return productService.getProductById(productId);
    }

    @Transactional("transactionManager")
    public ProductDto updateImage(Long productId, org.springframework.web.multipart.MultipartFile image) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product không tồn tại"));
        product.setImage(productImageStorageService.store(image));
        productRepository.save(product);
        return productService.getProductById(productId);
    }
}
