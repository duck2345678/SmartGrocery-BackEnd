package com.smartgrocery.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ProductImageStorageServiceTest {

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void storesPngWithUuidNameAndReturnsPublicPath(@TempDir Path tempDir) throws Exception {
        ProductImageStorageService service = new ProductImageStorageService();
        Path uploadDir = tempDir.resolve("public/uploads/products");
        setField(service, "productsDir", uploadDir.toString());
        setField(service, "maxBytes", 2L * 1024 * 1024);

        byte[] bytes = new byte[1024];
        MockMultipartFile file = new MockMultipartFile("image", "x.png", "image/png", bytes);

        String path = service.store(file);
        assertNotNull(path);
        assertTrue(path.startsWith("/uploads/products/"));
        assertTrue(path.endsWith(".png"));

        Path stored = uploadDir.resolve(path.substring("/uploads/products/".length()));
        assertTrue(Files.exists(stored));
        assertEquals(1024, Files.size(stored));
    }

    @Test
    void rejectsOver2Mb(@TempDir Path tempDir) {
        ProductImageStorageService service = new ProductImageStorageService();
        setField(service, "productsDir", tempDir.resolve("public/uploads/products").toString());
        setField(service, "maxBytes", 2L * 1024 * 1024);

        byte[] bytes = new byte[(2 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile("image", "x.png", "image/png", bytes);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.store(file));
        assertTrue(ex.getMessage().toLowerCase().contains("2 mb"));
    }

    @Test
    void rejectsUnsupportedFormat(@TempDir Path tempDir) {
        ProductImageStorageService service = new ProductImageStorageService();
        setField(service, "productsDir", tempDir.resolve("public/uploads/products").toString());
        setField(service, "maxBytes", 2L * 1024 * 1024);

        byte[] bytes = new byte[100];
        MockMultipartFile file = new MockMultipartFile("image", "x.gif", "image/gif", bytes);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.store(file));
        assertTrue(ex.getMessage().toLowerCase().contains("định dạng"));
    }
}

