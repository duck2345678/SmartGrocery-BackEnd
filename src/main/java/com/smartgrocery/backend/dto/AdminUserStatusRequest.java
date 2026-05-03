package com.smartgrocery.backend.dto;

import lombok.Data;

@Data
public class AdminUserStatusRequest {
    private String status;
    private String reason;
}
