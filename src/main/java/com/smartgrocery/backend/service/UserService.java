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
        if (request.getPhone() != null) {
            String phone = request.getPhone().trim();
            if (phone.isEmpty()) {
                throw new IllegalArgumentException("Số điện thoại không được để trống.");
            }
            if (!phone.matches("^(\\+84|0)\\d{9,10}$")) {
                throw new IllegalArgumentException("Số điện thoại không hợp lệ (phải gồm 10 chữ số).");
            }
            if (!phone.equals(user.getPhone())) {
                boolean exists = userRepository.existsByPhoneAndIdNot(phone, userId);
                if (exists) {
                    throw new IllegalArgumentException("Số điện thoại này đã được sử dụng bởi một tài khoản khác.");
                }
            }
            user.setPhone(phone);
        }
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

        User saved = userRepository.save(user);
        return mapToDto(saved);
    }

    private void validateAddress(UserAddressDto request, User user) {
        String receiverName = request.getReceiverName() != null && !request.getReceiverName().isBlank()
                ? request.getReceiverName().trim() : user.getFullName();
        String receiverPhone = request.getReceiverPhone() != null && !request.getReceiverPhone().isBlank()
                ? request.getReceiverPhone().trim() : user.getPhone();
                
        if (receiverName == null || receiverName.isBlank()) {
            throw new IllegalArgumentException("Tên người nhận không được để trống.");
        }
        if (receiverPhone == null || receiverPhone.isBlank()) {
            throw new IllegalArgumentException("Số điện thoại nhận hàng không được để trống.");
        }
        String cleanPhone = receiverPhone.replaceAll("\\s+", "");
        if (!cleanPhone.matches("^(\\+84|0)\\d{9,10}$")) {
            throw new IllegalArgumentException("Số điện thoại nhận hàng không hợp lệ (phải gồm 10 chữ số).");
        }
        if (request.getStreetAddress() == null || request.getStreetAddress().isBlank()) {
            throw new IllegalArgumentException("Số nhà và tên đường không được để trống.");
        }
        if (request.getWard() == null || request.getWard().isBlank()) {
            throw new IllegalArgumentException("Phường/Xã không được để trống.");
        }
        if (request.getDistrict() == null || request.getDistrict().isBlank()) {
            throw new IllegalArgumentException("Quận/Huyện không được để trống.");
        }
        if (request.getCity() == null || request.getCity().isBlank()) {
            throw new IllegalArgumentException("Tỉnh/Thành phố không được để trống.");
        }
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

        validateAddress(request, user);

        String rName = request.getReceiverName() != null && !request.getReceiverName().isBlank()
                ? request.getReceiverName().trim() : user.getFullName();
        String rPhone = request.getReceiverPhone() != null && !request.getReceiverPhone().isBlank()
                ? request.getReceiverPhone().trim() : user.getPhone();

        UserAddress address = UserAddress.builder()
                .user(user)
                .addressType(request.getAddressType())
                .receiverName(rName)
                .receiverPhone(rPhone)
                .streetAddress(request.getStreetAddress().trim())
                .ward(request.getWard().trim())
                .district(request.getDistrict().trim())
                .city(request.getCity().trim())
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

        validateAddress(request, user);

        String rName = request.getReceiverName() != null && !request.getReceiverName().isBlank()
                ? request.getReceiverName().trim() : user.getFullName();
        String rPhone = request.getReceiverPhone() != null && !request.getReceiverPhone().isBlank()
                ? request.getReceiverPhone().trim() : user.getPhone();

        address.setAddressType(request.getAddressType());
        address.setReceiverName(rName);
        address.setReceiverPhone(rPhone);
        address.setStreetAddress(request.getStreetAddress().trim());
        address.setWard(request.getWard().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setCity(request.getCity().trim());

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
