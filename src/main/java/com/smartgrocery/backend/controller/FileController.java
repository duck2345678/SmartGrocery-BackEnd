package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.security.SecurityUtils;
import com.smartgrocery.backend.service.SupabaseStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "Files", description = "Upload files (staff proof images)")
@RequiredArgsConstructor
public class FileController {

    private final SupabaseStorageService storageService;

    @Operation(summary = "Upload file (multipart) and return public URL")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestPart("file") MultipartFile file) {
        if (!SecurityUtils.hasAnyRole("CUSTOMER", "STAFF", "ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
        String folder = "misc";
        if (SecurityUtils.hasAnyRole("ADMIN")) {
            folder = "admin";
        } else if (SecurityUtils.hasAnyRole("STAFF")) {
            folder = "staff";
        } else if (SecurityUtils.hasAnyRole("CUSTOMER")) {
            folder = "avatars";
        }
        String url = storageService.upload(file, folder);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
