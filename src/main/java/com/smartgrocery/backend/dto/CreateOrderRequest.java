package com.smartgrocery.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreateOrderRequest {
    private Long addressId;
    private String paymentMethod;
    private String customerNote;
    private List<OrderItemRequest> items;
}
