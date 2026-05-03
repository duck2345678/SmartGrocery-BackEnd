package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.User;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"role"})
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);
    Optional<User> findByPhone(String phone);
    
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"role"})
    Optional<User> findByFirebaseUid(String firebaseUid);
    java.util.List<User> findByRole_Name(String roleName);
    java.util.List<User> findByRole_NameAndStatus(String roleName, String status);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByPhoneAndIdNot(String phone, Long id);
}
