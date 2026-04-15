package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String firebaseUid;
    private String email;
    private String phone;
    private String fullName;
    private String avatarUrl;
    private String status;
    private String roleName;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
}
