package com.smartgrocery.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreateOrderRequest {
    private Long addressId;
    private String paymentMethod;
    private String voucherCode;
    private String customerNote;
    private List<OrderItemRequest> items;
    private Boolean aiGenerated;
    private String aiListCode;
    private String aiListName;
}
