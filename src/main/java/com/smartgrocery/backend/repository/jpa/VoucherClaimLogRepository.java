package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.VoucherClaimLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoucherClaimLogRepository extends JpaRepository<VoucherClaimLog, Long> {
}
