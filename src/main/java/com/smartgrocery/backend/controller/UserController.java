package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.UserAddressDto;
import com.smartgrocery.backend.dto.UserDto;
import com.smartgrocery.backend.dto.UpdateUserRequest;
import com.smartgrocery.backend.dto.CreateAddressRequest;
import com.smartgrocery.backend.dto.UpdateAddressRequest;
import com.smartgrocery.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@CrossOrigin(origins = "*")
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

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin người dùng")
    public ResponseEntity<UserDto> updateUserProfile(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUserProfile(id, request));
    }

    @GetMapping("/{id}/addresses")
    @Operation(summary = "Lấy danh sách địa chỉ của người dùng")
    public ResponseEntity<List<UserAddressDto>> getUserAddresses(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserAddresses(id));
    }

    @PostMapping("/{id}/addresses")
    @Operation(summary = "Thêm địa chỉ mới cho người dùng")
    public ResponseEntity<UserAddressDto> createAddress(@PathVariable Long id, @Valid @RequestBody CreateAddressRequest request) {
        return ResponseEntity.ok(userService.createAddress(id, request));
    }

    @PutMapping("/{id}/addresses/{addressId}")
    @Operation(summary = "Cập nhật địa chỉ của người dùng")
    public ResponseEntity<UserAddressDto> updateAddress(@PathVariable Long id, @PathVariable Long addressId, @Valid @RequestBody UpdateAddressRequest request) {
        return ResponseEntity.ok(userService.updateAddress(id, addressId, request));
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    @Operation(summary = "Xóa địa chỉ của người dùng")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id, @PathVariable Long addressId) {
        userService.deleteAddress(id, addressId);
        return ResponseEntity.noContent().build();
    }
}
