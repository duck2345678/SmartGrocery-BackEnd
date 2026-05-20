package com.smartgrocery.backend.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ResetPasswordRequest {
    private String email;
    private String otp;
    private String newPassword;
}
