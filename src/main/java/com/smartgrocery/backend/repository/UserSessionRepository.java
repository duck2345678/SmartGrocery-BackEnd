package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.UserSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    long countByUser_IdAndRevokedFalseAndExpiresAtAfter(Long userId, LocalDateTime now);

    @Query("""
            select us
            from UserSession us
            where us.user.id = :userId
              and us.revoked = false
              and us.expiresAt > :now
            order by coalesce(us.lastUsedAt, us.createdAt) asc
            """)
    List<UserSession> findActiveSessionsLruAsc(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Modifying
    @Transactional
    @Query("""
            update UserSession us
            set us.revoked = true
            where us.user.id = :userId
              and us.revoked = false
            """)
    int revokeAllActiveSessionsByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("""
            delete from UserSession us
            where us.revoked = true
               or us.expiresAt <= :now
            """)
    int deleteRevokedOrExpired(@Param("now") LocalDateTime now);
}
