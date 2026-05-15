package com.smartgrocery.backend.repository.jpa;
import com.smartgrocery.backend.entity.UserVoucherUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserVoucherUsageRepository extends JpaRepository<UserVoucherUsage, Long> {
    Optional<UserVoucherUsage> findByUser_IdAndVoucher_Id(Long userId, Long voucherId);
}
