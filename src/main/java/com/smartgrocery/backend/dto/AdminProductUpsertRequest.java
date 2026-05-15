package com.smartgrocery.backend.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Data
public class AdminProductUpsertRequest {
    @NotBlank(message = "Product code is required")
    private String productCode;

    @NotBlank(message = "Product name is required")
    private String name;

    @NotNull(message = "Category is required")
    private Long categoryId;

    private String shortDescription;
    private String description;
    private String originCountry;
    private String status;
    private Boolean isFeatured;

    private String sku;
    private String barcode;
    private String variantName;
    private String color;
    private String size;
    private String unit;

    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be greater than or equal to 0")
    private BigDecimal netPrice;

    @Min(value = 0, message = "Stock must be greater than or equal to 0")
    private Integer stock;

    private String variantsJson;
    private MultipartFile image;
}
