package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.UserAddressDto;
import com.smartgrocery.backend.dto.UserDto;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserAddress;
import com.smartgrocery.backend.repository.jpa.UserAddressRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import com.smartgrocery.backend.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Transactional(readOnly = true)
    public UserDto getUserProfile(Long id) {
        SecurityUtils.verifyOwnershipOrAdmin(id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        return mapToDto(user);
    }

    @Transactional(readOnly = true)
    public List<UserAddressDto> getUserAddresses(Long userId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        return userAddressRepository.findByUser_Id(userId).stream()
                .map(this::mapToAddressDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDto updateUserProfile(Long userId, UserDto request) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

        User saved = userRepository.save(user);
        return mapToDto(saved);
    }

    @Transactional
    public UserAddressDto addAddress(Long userId, UserAddressDto request) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        List<UserAddress> existingAddresses = userAddressRepository.findByUser_Id(userId);
        
        // Rule: First address is always default
        boolean isFirst = existingAddresses.isEmpty();
        boolean shouldBeDefault = isFirst || Boolean.TRUE.equals(request.getIsDefault());

        if (shouldBeDefault) {
            resetDefaultAddresses(userId);
        }

        UserAddress address = UserAddress.builder()
                .user(user)
                .addressType(request.getAddressType())
                .receiverName(user.getFullName()) // Auto-take from customer
                .receiverPhone(user.getPhone())   // Auto-take from customer
                .streetAddress(request.getStreetAddress())
                .ward(request.getWard())
                .district(request.getDistrict())
                .city(request.getCity())
                .isDefault(shouldBeDefault)
                .build();

        return mapToAddressDto(userAddressRepository.save(address));
    }

    @Transactional
    public UserAddressDto updateAddress(Long userId, Long addressId, UserAddressDto request) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUser().getId().equals(userId)) {
            throw new RuntimeException("Forbidden");
        }

        // Rule: If it's the only address, it must stay default
        List<UserAddress> all = userAddressRepository.findByUser_Id(userId);
        boolean isOnlyOne = all.size() == 1;
        boolean settingDefault = Boolean.TRUE.equals(request.getIsDefault());

        if (isOnlyOne) {
            address.setIsDefault(true);
        } else if (settingDefault && !Boolean.TRUE.equals(address.getIsDefault())) {
            resetDefaultAddresses(userId);
            address.setIsDefault(true);
        }

        address.setAddressType(request.getAddressType());
        // Sync with current customer info if they were removed from form
        address.setReceiverName(user.getFullName());
        address.setReceiverPhone(user.getPhone());
        
        address.setStreetAddress(request.getStreetAddress());
        address.setWard(request.getWard());
        address.setDistrict(request.getDistrict());
        address.setCity(request.getCity());

        return mapToAddressDto(userAddressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUser().getId().equals(userId)) {
            throw new RuntimeException("Forbidden");
        }

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        userAddressRepository.delete(address);

        // Rule: If we deleted the default one, pick another one to be default
        if (wasDefault) {
            List<UserAddress> remaining = userAddressRepository.findByUser_Id(userId);
            if (!remaining.isEmpty()) {
                UserAddress nextDefault = remaining.get(0);
                nextDefault.setIsDefault(true);
                userAddressRepository.save(nextDefault);
            }
        }
    }

    @Transactional
    public void setDefaultAddress(Long userId, Long addressId) {
        SecurityUtils.verifyOwnershipOrAdmin(userId);
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        
        if (!address.getUser().getId().equals(userId)) {
            throw new RuntimeException("Forbidden");
        }

        if (!Boolean.TRUE.equals(address.getIsDefault())) {
            resetDefaultAddresses(userId);
            address.setIsDefault(true);
            userAddressRepository.save(address);
        }
    }

    private void resetDefaultAddresses(Long userId) {
        List<UserAddress> addresses = userAddressRepository.findByUser_Id(userId);
        for (UserAddress addr : addresses) {
            if (Boolean.TRUE.equals(addr.getIsDefault())) {
                addr.setIsDefault(false);
                userAddressRepository.save(addr);
            }
        }
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
