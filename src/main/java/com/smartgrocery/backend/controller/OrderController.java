package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.CreateOrderRequest;
import com.smartgrocery.backend.dto.OrderDto;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order", description = "Quản lý Đơn đặt hàng")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Lấy danh sách đơn hàng của tôi")
    @GetMapping("/my-orders")
    public ResponseEntity<List<OrderDto>> getUserOrders(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.getUserOrders(user.getId()));
    }

    @Operation(summary = "Tạo đơn hàng mới (Checkout)")
    @PostMapping("/checkout")
    public ResponseEntity<OrderDto> createOrder(
            @AuthenticationPrincipal User user,
            @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity.ok(orderService.createOrder(user, request));
    }
}
