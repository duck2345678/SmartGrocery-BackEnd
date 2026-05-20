package com.smartgrocery.backend.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ForgotPasswordRequest {
    private String email;
}
