package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.repository.jpa.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class DataSanitizationService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;


    @Value("${app.sanitization.auto-start:false}")
    private boolean autoStart;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (autoStart) {
            log.info("Auto-sanitization is enabled. Starting data cleanup...");
            sanitizeAndClear();
        }
    }

    @Transactional(value = "transactionManager")
    public void sanitizeAndClear() {
        log.info("Starting data sanitization and clearing...");

        // 1. Clear orphaned data if any
        log.info("Checking for orphaned OrderItems...");
        
        orderItemRepository.findAll().forEach(oi -> {
            if (oi.getVariant() == null) {
                log.warn("Found orphaned OrderItem #{} for order #{}. No variant assigned!", oi.getId(), oi.getOrder().getId());
            }
        });

        // 3. Fix missing variants and data (Rà soát sản phẩm lỗi)
        log.info("Fixing products and variants data...");
        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            List<ProductVariant> variants = variantRepository.findByProduct_Id(product.getId());
            if (variants.isEmpty()) {
                log.info("Product #{} '{}' has no variants. Creating default variant...", product.getId(), product.getName());
                ProductVariant v = ProductVariant.builder()
                        .product(product)
                        .sku("SKU-" + product.getId() + "-" + UUID.randomUUID().toString().substring(0, 8))
                        .unit("unit")
                        .netPrice(BigDecimal.ZERO)
                        .vatPercent(BigDecimal.ZERO)
                        .status("ACTIVE")
                        .variantName("Mặc định")
                        .build();
                variantRepository.save(v);
            } else {
                for (ProductVariant v : variants) {
                    boolean changed = false;
                    if (v.getSku() == null || v.getSku().trim().isEmpty()) {
                        v.setSku("SKU-FIX-" + v.getId() + "-" + UUID.randomUUID().toString().substring(0, 8));
                        changed = true;
                    }
                    if (v.getNetPrice() == null) {
                        v.setNetPrice(BigDecimal.ZERO);
                        changed = true;
                    }
                    if (v.getUnit() == null || v.getUnit().trim().isEmpty()) {
                        v.setUnit("unit");
                        changed = true;
                    }
                    if (v.getStatus() == null) {
                        v.setStatus("ACTIVE");
                        changed = true;
                    }
                    if (changed) {
                        log.info("Fixed variant data for SKU: {}", v.getSku());
                        variantRepository.save(v);
                    }
                }
            }
        }

        log.info("Data sanitization and clearing completed successfully!");
    }
}
