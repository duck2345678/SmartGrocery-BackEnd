package com.smartgrocery.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminProductStatusRequest {
    @NotBlank(message = "Status is required")
    private String status;
    private String reason;
}
