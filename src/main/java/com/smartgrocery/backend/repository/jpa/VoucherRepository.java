package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.Voucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
              and v.hidden = false
              and (v.validFrom is null or v.validFrom <= :now)
              and (v.validUntil is null or v.validUntil >= :now)
            """)
    List<Voucher> findAvailableAt(@Param("now") LocalDateTime now);

    @Query("""
            select v from Voucher v
            where v.active = true
              and (
                v.hidden = false
                or (v.hidden = true and v.assignedUser.id = :userId)
              )
              and (v.validFrom is null or v.validFrom <= :now)
              and (v.validUntil is null or v.validUntil >= :now)
            """)
    List<Voucher> findVisibleForUserAt(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select v from Voucher v
            where v.active = true
              and v.hidden = true
              and v.revealTrigger = 'AI_ORDER_COMPLETED'
              and v.assignedUser is null
              and (v.validFrom is null or v.validFrom <= :now)
              and (v.validUntil is null or v.validUntil >= :now)
              and (v.usageLimit is null or v.usageCount is null or v.usageCount < v.usageLimit)
            order by v.createdAt asc
            """)
    List<Voucher> findUnassignedAiRewardCandidates(@Param("now") LocalDateTime now);
}
