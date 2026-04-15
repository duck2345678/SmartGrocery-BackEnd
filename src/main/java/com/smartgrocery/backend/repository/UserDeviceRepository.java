package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByFcmToken(String fcmToken);
    void deleteByFcmToken(String fcmToken);
}
