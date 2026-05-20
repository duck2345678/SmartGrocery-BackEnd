package com.smartgrocery.backend.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VerifyEmailRequest {
    private String email;
    private String otp;
}
