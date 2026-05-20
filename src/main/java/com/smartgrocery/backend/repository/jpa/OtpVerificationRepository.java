package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    // Tìm OTP hợp lệ (PENDING + chưa hết hạn) gần nhất
    @Query("SELECT o FROM OtpVerification o WHERE o.email = :email AND o.purpose = :purpose AND o.status = 'PENDING' AND o.expiresAt > :now ORDER BY o.createdAt DESC")
    Optional<OtpVerification> findLatestActive(@Param("email") String email,
                                               @Param("purpose") String purpose,
                                               @Param("now") LocalDateTime now);

    // Huỷ tất cả OTP cũ trước khi tạo mới
    @Modifying
    @Query("UPDATE OtpVerification o SET o.status = 'CANCELLED' WHERE o.email = :email AND o.purpose = :purpose AND o.status = 'PENDING'")
    void cancelAllPending(@Param("email") String email, @Param("purpose") String purpose);

    // Đếm số lần gửi trong vòng 1 giờ (rate limit)
    @Query("SELECT COUNT(o) FROM OtpVerification o WHERE o.email = :email AND o.purpose = :purpose AND o.createdAt > :since")
    long countSentSince(@Param("email") String email,
                        @Param("purpose") String purpose,
                        @Param("since") LocalDateTime since);
}
