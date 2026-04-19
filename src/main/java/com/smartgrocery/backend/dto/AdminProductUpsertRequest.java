package com.smartgrocery.backend.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Data
public class AdminProductUpsertRequest {
    private String productCode;
    private String name;
    private Long categoryId;
    private String shortDescription;
    private String description;
    private String originCountry;
    private String status;
    private Boolean isFeatured;

    private String sku;
    private String barcode;
    private String variantName;
    private String unit;
    private BigDecimal netPrice;
    private Integer stock;

    private MultipartFile image;
}

