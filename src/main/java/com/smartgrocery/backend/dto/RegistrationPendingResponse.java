package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RegistrationPendingResponse {
    private String email;
    private boolean requiresEmailVerification;
    private int expiresInSeconds;
    private String message;
}
