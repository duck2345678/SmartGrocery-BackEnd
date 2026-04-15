package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.UserAddressDto;
import com.smartgrocery.backend.dto.UserDto;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserAddress;
import com.smartgrocery.backend.repository.UserAddressRepository;
import com.smartgrocery.backend.repository.UserRepository;
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
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        return mapToDto(user);
    }

    public List<UserAddressDto> getUserAddresses(Long userId) {
        return userAddressRepository.findByUser_Id(userId).stream()
                .map(this::mapToAddressDto)
                .collect(Collectors.toList());
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
