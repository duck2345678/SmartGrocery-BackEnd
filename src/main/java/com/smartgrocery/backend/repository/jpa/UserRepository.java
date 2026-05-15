package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.User;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
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

    @Query("""
            select distinct u
            from User u
            where u.role.name = :roleName
              and u.status = :status
              and exists (
                  select 1
                  from AttendanceRecord ar
                  where ar.user = u
                    and ar.workDate = :workDate
                    and ar.checkInAt is not null
                    and ar.checkOutAt is null
              )
            """)
    List<User> findAvailableStaffInShift(
            @Param("roleName") String roleName,
            @Param("status") String status,
            @Param("workDate") LocalDate workDate
    );
}
