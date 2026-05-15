package com.smartgrocery.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class SupabaseStorageService {

    @Value("${SUPABASE_URL}")
    private String supabaseUrl;

    @Value("${SUPABASE_SERVICE_ROLE_KEY}")
    private String supabaseKey;

    private final String bucketName = "images";
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Uploads a MultipartFile to Supabase Storage.
     * @param file The file to upload.
     * @param folder The folder in the bucket (e.g., "products", "staff").
     * @return The public URL of the uploaded file.
     */
    public String upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String extension = getExtension(file.getOriginalFilename());
        String fileName = folder + "/" + UUID.randomUUID() + "." + extension;

        try {
            return uploadBytes(file.getBytes(), fileName, file.getContentType());
        } catch (IOException e) {
            log.error("Failed to read file bytes", e);
            throw new RuntimeException("Failed to upload to Supabase");
        }
    }

    /**
     * Uploads a local file Path to Supabase Storage.
     */
    public String upload(Path localPath, String folder) {
        String fileName = folder + "/" + localPath.getFileName().toString();
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(localPath);
            String contentType = java.nio.file.Files.probeContentType(localPath);
            return uploadBytes(bytes, fileName, contentType != null ? contentType : "application/octet-stream");
        } catch (IOException e) {
            log.error("Failed to read local file", e);
            throw new RuntimeException("Failed to migrate file to Supabase");
        }
    }

    private String uploadBytes(byte[] bytes, String fileName, String contentType) {
        String url = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fileName;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(supabaseKey);
        headers.set("apikey", supabaseKey);
        headers.setContentType(MediaType.parseMediaType(contentType));

        HttpEntity<byte[]> entity = new HttpEntity<>(bytes, headers);

        try {
            restTemplate.postForEntity(url, entity, Map.class);
            
            // Return the public URL
            // Format: https://[project-id].supabase.co/storage/v1/object/public/[bucket]/[path]
            return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + fileName;
        } catch (Exception e) {
            log.error("Supabase upload failed for {}: {}", fileName, e.getMessage());
            throw new RuntimeException("Supabase upload failed: " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
