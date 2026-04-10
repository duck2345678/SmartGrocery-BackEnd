package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByPhone(String phone);
    Optional<User> findByFirebaseUid(String firebaseUid);
    java.util.List<User> findByRole_Name(String roleName);
}
