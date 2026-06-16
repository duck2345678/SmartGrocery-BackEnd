package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.AddToCartRequest;
import com.smartgrocery.backend.dto.CartDto;
import com.smartgrocery.backend.dto.UpdateCartItemRequest;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/carts")
@Tag(name = "Cart", description = "Quản lý Giỏ hàng")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Lấy giỏ hàng của User")
    @GetMapping("/my-cart")
    public ResponseEntity<CartDto> getCart(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(cartService.getCart(user.getId()));
    }

    @Operation(summary = "Thêm sản phẩm vào giỏ hàng")
    @PostMapping("/add")
    public ResponseEntity<CartDto> addToCart(
            @AuthenticationPrincipal User user,
            @RequestBody AddToCartRequest request
    ) {
        return ResponseEntity.ok(cartService.addToCart(user, request));
    }

    @Operation(summary = "Thêm nhiều sản phẩm vào giỏ hàng cùng lúc (AI Chat batch)")
    @PostMapping("/batch-add")
    public ResponseEntity<CartDto> batchAddToCart(
            @AuthenticationPrincipal User user,
            @RequestBody java.util.List<AddToCartRequest> requests
    ) {
        return ResponseEntity.ok(cartService.batchAddToCart(user, requests));
    }

    @Operation(summary = "Xóa nhiều sản phẩm khỏi giỏ hàng")
    @DeleteMapping("/items")
    public ResponseEntity<CartDto> removeCartItems(
            @AuthenticationPrincipal User user,
            @RequestBody java.util.List<Long> cartItemIds
    ) {
        return ResponseEntity.ok(cartService.removeCartItems(user, cartItemIds));
    }

    @Operation(summary = "Xóa sản phẩm khỏi giỏ hàng")
    @DeleteMapping("/item/{cartItemId}")
    public ResponseEntity<CartDto> removeCartItem(
            @AuthenticationPrincipal User user,
            @PathVariable Long cartItemId
    ) {
        return ResponseEntity.ok(cartService.removeCartItem(user, cartItemId));
    }

    @Operation(summary = "Xóa sạch giỏ hàng")
    @DeleteMapping("/clear")
    public ResponseEntity<CartDto> clearCart(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(cartService.clearCart(user));
    }

    @Operation(summary = "Cập nhật số lượng sản phẩm trong giỏ hàng (quantity=0 sẽ xoá)")
    @PatchMapping("/item/{cartItemId}")
    public ResponseEntity<CartDto> updateQuantity(
            @AuthenticationPrincipal User user,
            @PathVariable Long cartItemId,
            @RequestBody UpdateCartItemRequest request
    ) {
        return ResponseEntity.ok(cartService.updateCartItem(user, cartItemId, request.getQuantity()));
    }
}
