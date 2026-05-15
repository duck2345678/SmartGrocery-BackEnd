package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.repository.jpa.OrderRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
public class ImageMigrationService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SupabaseStorageService supabaseStorageService;

    @Value("${app.upload.products-dir}")
    private String productsDir;

    @Value("${app.upload.staff-dir}")
    private String staffDir;

    @Value("${app.migration.auto-start}")
    private boolean autoStart;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (autoStart) {
            log.info("Auto-migration is enabled. Starting migration process...");
            migrateAll();
        }
    }

    public void migrateAll() {
        log.info("Starting image migration to Supabase...");
        migrateProducts();
        migrateStaffPhotos();
        log.info("Migration completed!");
    }

    private void migrateProducts() {
        List<Product> products = productRepository.findAll();
        Path baseDir = Paths.get(productsDir).toAbsolutePath().normalize();

        for (Product product : products) {
            String path = product.getImage();
            if (path != null && path.startsWith("/uploads/products/")) {
                String fileName = path.substring("/uploads/products/".length());
                Path localFile = baseDir.resolve(fileName);

                if (Files.exists(localFile)) {
                    try {
                        log.info("Migrating product image: {}", fileName);
                        String newUrl = supabaseStorageService.upload(localFile, "products");
                        product.setImage(newUrl);
                        productRepository.save(product);
                    } catch (Exception e) {
                        log.error("Failed to migrate product image {}: {}", fileName, e.getMessage());
                    }
                }
            }
        }
    }

    private void migrateStaffPhotos() {
        List<Order> orders = orderRepository.findAll();
        Path baseDir = Paths.get(staffDir).toAbsolutePath().normalize();

        for (Order order : orders) {
            // Packing Photo
            String packPath = order.getPackingPhotoUrl();
            if (packPath != null && packPath.startsWith("/uploads/staff/")) {
                migrateOrderPhoto(order, packPath, baseDir, true);
            }

            // Delivery Photo
            String delivPath = order.getDeliveryPhotoUrl();
            if (delivPath != null && delivPath.startsWith("/uploads/staff/")) {
                migrateOrderPhoto(order, delivPath, baseDir, false);
            }
        }
    }

    private void migrateOrderPhoto(Order order, String path, Path baseDir, boolean isPacking) {
        String fileName = path.substring("/uploads/staff/".length());
        Path localFile = baseDir.resolve(fileName);

        if (Files.exists(localFile)) {
            try {
                log.info("Migrating staff photo: {}", fileName);
                String newUrl = supabaseStorageService.upload(localFile, "staff");
                if (isPacking) {
                    order.setPackingPhotoUrl(newUrl);
                } else {
                    order.setDeliveryPhotoUrl(newUrl);
                }
                orderRepository.save(order);
            } catch (Exception e) {
                log.error("Failed to migrate staff photo {}: {}", fileName, e.getMessage());
            }
        }
    }
}
