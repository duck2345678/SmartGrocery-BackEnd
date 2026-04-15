package com.smartgrocery.backend.dto;

import lombok.Data;

@Data
public class CheckoutRequest {
    private Long userId;
    private Long addressId;
    private String paymentMethod;
    private String voucherCode;
    private String customerNote;
}
