package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.UserAddressDto;
import com.smartgrocery.backend.dto.UserDto;
import com.smartgrocery.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "API quản lý thông tin người dùng")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin người dùng theo ID")
    public ResponseEntity<UserDto> getUserProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserProfile(id));
    }

    @GetMapping("/{id}/addresses")
    @Operation(summary = "Lấy danh sách địa chỉ của người dùng")
    public ResponseEntity<List<UserAddressDto>> getUserAddresses(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserAddresses(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật hồ sơ người dùng")
    public ResponseEntity<UserDto> updateProfile(@PathVariable Long id, @RequestBody UserDto request) {
        return ResponseEntity.ok(userService.updateUserProfile(id, request));
    }

    @PostMapping("/{id}/addresses")
    @Operation(summary = "Thêm địa chỉ mới")
    public ResponseEntity<UserAddressDto> addAddress(@PathVariable Long id, @RequestBody UserAddressDto request) {
        return ResponseEntity.ok(userService.addAddress(id, request));
    }

    @PutMapping("/{id}/addresses/{addressId}")
    @Operation(summary = "Cập nhật địa chỉ")
    public ResponseEntity<UserAddressDto> updateAddress(
            @PathVariable Long id,
            @PathVariable Long addressId,
            @RequestBody UserAddressDto request
    ) {
        return ResponseEntity.ok(userService.updateAddress(id, addressId, request));
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    @Operation(summary = "Xóa địa chỉ")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id, @PathVariable Long addressId) {
        userService.deleteAddress(id, addressId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/addresses/{addressId}/default")
    @Operation(summary = "Thiết lập địa chỉ mặc định")
    public ResponseEntity<Void> setDefaultAddress(@PathVariable Long id, @PathVariable Long addressId) {
        userService.setDefaultAddress(id, addressId);
        return ResponseEntity.noContent().build();
    }
}
