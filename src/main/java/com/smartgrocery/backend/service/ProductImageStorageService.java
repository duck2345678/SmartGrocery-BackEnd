package com.smartgrocery.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductImageStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Value("${app.upload.products-dir}")
    private String productsDir;

    @Value("${app.upload.products-max-bytes}")
    private long maxBytes;

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh sản phẩm");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Ảnh vượt quá dung lượng tối đa 2 MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Định dạng ảnh không hợp lệ. Chỉ hỗ trợ jpg, png, webp");
        }

        String ext = switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Định dạng ảnh không hợp lệ. Chỉ hỗ trợ jpg, png, webp");
        };

        String fileName = UUID.randomUUID() + "." + ext;
        Path dir = Paths.get(productsDir).toAbsolutePath().normalize();
        Path target = dir.resolve(fileName).normalize();

        try {
            Files.createDirectories(dir);
            Files.write(target, file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu ảnh sản phẩm");
        }

        return "/uploads/products/" + fileName;
    }
}

