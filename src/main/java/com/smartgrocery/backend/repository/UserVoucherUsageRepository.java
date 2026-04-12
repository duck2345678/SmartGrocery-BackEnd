package com.smartgrocery.backend.repository;
import com.smartgrocery.backend.entity.UserVoucherUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserVoucherUsageRepository extends JpaRepository<UserVoucherUsage, Long> {}