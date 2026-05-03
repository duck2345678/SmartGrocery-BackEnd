package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByVoucherCode(String voucherCode);
    @Query("""
            select v from Voucher v
            where v.active = true
              and (v.validFrom is null or v.validFrom <= :now)
              and (v.validUntil is null or v.validUntil >= :now)
            """)
    List<Voucher> findAvailableAt(@Param("now") LocalDateTime now);
}
