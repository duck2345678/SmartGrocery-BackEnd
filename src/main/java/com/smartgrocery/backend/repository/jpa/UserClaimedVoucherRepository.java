package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.UserClaimedVoucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserClaimedVoucherRepository extends JpaRepository<UserClaimedVoucher, Long> {
    Optional<UserClaimedVoucher> findByUser_IdAndVoucher_Id(Long userId, Long voucherId);

                @Query("""
                                                select c from UserClaimedVoucher c
                                                join fetch c.voucher v
                                                where c.user.id = :userId
                                                        and c.used = false
                                                        and c.status = 'ACTIVE'
                                                        and (c.expiresAt is null or c.expiresAt >= :now)
                                                order by c.claimedAt desc
                                                """)
                List<UserClaimedVoucher> findActiveClaimedVouchers(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("""
            select c from UserClaimedVoucher c
            where c.user.id = :userId
              and c.voucher.id = :voucherId
              and c.status = 'ACTIVE'
              and c.used = false
              and (c.expiresAt is null or c.expiresAt >= :now)
            """)
    Optional<UserClaimedVoucher> findUsableClaim(
            @Param("userId") Long userId,
            @Param("voucherId") Long voucherId,
            @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from UserClaimedVoucher c
            where c.user.id = :userId
              and c.voucher.id = :voucherId
              and c.status = 'ACTIVE'
              and c.used = false
              and (c.expiresAt is null or c.expiresAt >= :now)
            """)
    Optional<UserClaimedVoucher> findUsableClaimForUpdate(
            @Param("userId") Long userId,
            @Param("voucherId") Long voucherId,
            @Param("now") LocalDateTime now
    );
}
