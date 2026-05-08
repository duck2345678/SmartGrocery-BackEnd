package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.UserAddressDto;
import com.smartgrocery.backend.dto.UserDto;
import com.smartgrocery.backend.dto.UpdateUserRequest;
import com.smartgrocery.backend.dto.CreateAddressRequest;
import com.smartgrocery.backend.dto.UpdateAddressRequest;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserAddress;
import com.smartgrocery.backend.repository.UserAddressRepository;
import com.smartgrocery.backend.repository.UserRepository;
import com.smartgrocery.backend.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager", readOnly = true)
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAddressRepository userAddressRepository;

    public UserDto getUserProfile(Long id) {
        SecurityUtils.verifyOwnershipOrAdmin(id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        return mapToDto(user);
    }

    @Transactional
    public UserDto updateUserProfile(Long id, UpdateUserRequest request) {
        SecurityUtils.verifyOwnershipOrAdmin(id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        user = userRepository.save(user);
        return mapToDto(user);
    }

    public List<UserAddressDto> getUserAddresses(Long userId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        return userAddressRepository.findByUser_Id(userId).stream()
                .map(this::mapToAddressDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserAddressDto createAddress(Long userId, CreateAddressRequest request) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        // If this is set as default, unset other defaults
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            userAddressRepository.findByUser_Id(userId).forEach(addr -> {
                addr.setIsDefault(false);
                userAddressRepository.save(addr);
            });
        }

        UserAddress address = UserAddress.builder()
                .user(user)
                .addressType(request.getAddressType())
                .receiverName(request.getReceiverName())
                .receiverPhone(request.getReceiverPhone())
                .streetAddress(request.getStreetAddress())
                .ward(request.getWard())
                .district(request.getDistrict())
                .city(request.getCity())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();

        address = userAddressRepository.save(address);
        return mapToAddressDto(address);
    }

    @Transactional
    public UserAddressDto updateAddress(Long userId, Long addressId, UpdateAddressRequest request) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUser().getId().equals(userId)) {
            throw new RuntimeException("Address does not belong to user");
        }

        // If this is set as default, unset other defaults
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            userAddressRepository.findByUser_Id(userId).forEach(addr -> {
                if (!addr.getId().equals(addressId)) {
                    addr.setIsDefault(false);
                    userAddressRepository.save(addr);
                }
            });
        }

        if (request.getAddressType() != null) {
            address.setAddressType(request.getAddressType());
        }
        if (request.getReceiverName() != null) {
            address.setReceiverName(request.getReceiverName());
        }
        if (request.getReceiverPhone() != null) {
            address.setReceiverPhone(request.getReceiverPhone());
        }
        if (request.getStreetAddress() != null) {
            address.setStreetAddress(request.getStreetAddress());
        }
        if (request.getWard() != null) {
            address.setWard(request.getWard());
        }
        if (request.getDistrict() != null) {
            address.setDistrict(request.getDistrict());
        }
        if (request.getCity() != null) {
            address.setCity(request.getCity());
        }
        if (request.getIsDefault() != null) {
            address.setIsDefault(request.getIsDefault());
        }

        address = userAddressRepository.save(address);
        return mapToAddressDto(address);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUser().getId().equals(userId)) {
            throw new RuntimeException("Address does not belong to user");
        }

        userAddressRepository.delete(address);
    }

    private UserDto mapToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .firebaseUid(user.getFirebaseUid())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roleName(user.getRole().getName())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }

    private UserAddressDto mapToAddressDto(UserAddress address) {
        return UserAddressDto.builder()
                .id(address.getId())
                .addressType(address.getAddressType())
                .receiverName(address.getReceiverName())
                .receiverPhone(address.getReceiverPhone())
                .streetAddress(address.getStreetAddress())
                .ward(address.getWard())
                .district(address.getDistrict())
                .city(address.getCity())
                .isDefault(address.getIsDefault())
                .build();
    }
}
