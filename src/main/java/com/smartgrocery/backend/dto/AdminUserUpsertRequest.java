package com.smartgrocery.backend.dto;

import lombok.Data;

@Data
public class AdminUserUpsertRequest {
    private String fullName;
    private String email;
    private String phone;
    private String password;
    private String roleName;
    private String avatarUrl;
    private String status;
    private String reason;
}
