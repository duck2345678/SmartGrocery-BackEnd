package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.ProductDto;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/wishlist")
@Tag(name = "Customer - Wishlist", description = "Quản lý danh sách yêu thích")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @Operation(summary = "Lấy danh sách yêu thích")
    @GetMapping
    public ResponseEntity<List<ProductDto>> getWishlist(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(wishlistService.getWishlist(user));
    }

    @Operation(summary = "Thêm vào danh sách yêu thích")
    @PostMapping("/{productId}")
    public ResponseEntity<Void> addToWishlist(@AuthenticationPrincipal User user, @PathVariable Long productId) {
        wishlistService.addToWishlist(user, productId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Xóa khỏi danh sách yêu thích")
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFromWishlist(@AuthenticationPrincipal User user, @PathVariable Long productId) {
        wishlistService.removeFromWishlist(user, productId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Kiểm tra xem sản phẩm có trong wishlist không")
    @GetMapping("/check/{productId}")
    public ResponseEntity<Boolean> isInWishlist(@AuthenticationPrincipal User user, @PathVariable Long productId) {
        return ResponseEntity.ok(wishlistService.isInWishlist(user, productId));
    }
}
