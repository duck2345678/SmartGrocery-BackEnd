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

    @GetMapping("/{id}/addresses")
    @Operation(summary = "Lấy danh sách địa chỉ của người dùng")
    public ResponseEntity<List<UserAddressDto>> getUserAddresses(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserAddresses(id));
    }
}
