package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser_Id(Long userId);
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findTop200ByUser_IdAndStatusNotOrderByCreatedAtDesc(Long userId, String excludedStatus);

    @Modifying
    @Query("""
           update Order o
           set o.assignee.id = :staffId,
               o.leaseExpiresAt = :leaseExpiresAt,
               o.status = :assignedStatus
           where o.id = :orderId
             and o.status = :pendingStatus
             and (o.assignee is null or o.leaseExpiresAt is null or o.leaseExpiresAt < :now)
           """)
    int assignIfAvailable(
            @Param("orderId") Long orderId,
            @Param("staffId") Long staffId,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
            @Param("assignedStatus") String assignedStatus,
            @Param("pendingStatus") String pendingStatus,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
           update Order o
           set o.leaseExpiresAt = :leaseExpiresAt
           where o.id = :orderId
             and o.assignee.id = :staffId
             and o.status in :activeStatuses
             and o.leaseExpiresAt is not null
             and o.leaseExpiresAt >= :now
           """)
    int heartbeatLease(
            @Param("orderId") Long orderId,
            @Param("staffId") Long staffId,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
            @Param("activeStatuses") List<String> activeStatuses,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
           update Order o
           set o.assignee = null,
               o.leaseExpiresAt = null,
               o.status = :pendingStatus
           where o.id = :orderId
             and o.assignee.id = :staffId
             and o.status in :activeStatuses
           """)
    int releaseAssignment(
            @Param("orderId") Long orderId,
            @Param("staffId") Long staffId,
            @Param("pendingStatus") String pendingStatus,
            @Param("activeStatuses") List<String> activeStatuses
    );

    @Query("""
           select o from Order o
           where o.status = :pendingStatus
             and (o.assignee is null or o.leaseExpiresAt is null or o.leaseExpiresAt < :now)
           order by o.createdAt asc
           """)
    List<Order> findQueueForAssignment(@Param("pendingStatus") String pendingStatus, @Param("now") LocalDateTime now);

    @Query("""
           select o from Order o
           where o.status = :assignedStatus
             and o.assignee is not null
           order by o.updatedAt asc
           """)
    List<Order> findAssignedOrders(@Param("assignedStatus") String assignedStatus);

    @Modifying
    @Query("""
           update Order o
           set o.assignee = null,
               o.leaseExpiresAt = null,
               o.status = :pendingStatus
           where o.status in :activeStatuses
             and o.leaseExpiresAt is not null
             and o.leaseExpiresAt < :now
           """)
    int releaseExpiredLeases(
            @Param("pendingStatus") String pendingStatus,
            @Param("activeStatuses") List<String> activeStatuses,
            @Param("now") LocalDateTime now
    );

    @Query("""
           select count(o) from Order o
           where o.assignee.id = :staffId
             and o.status in :activeStatuses
             and o.leaseExpiresAt is not null
             and o.leaseExpiresAt >= :now
           """)
    long countActiveAssignments(
            @Param("staffId") Long staffId,
            @Param("activeStatuses") List<String> activeStatuses,
            @Param("now") LocalDateTime now
    );

    @Query("""
           select max(o.createdAt) from Order o
           where o.assignee.id = :staffId
           """)
    LocalDateTime findLastAssignedAt(@Param("staffId") Long staffId);

    @Query("""
           select o from Order o
           where o.assignee.id = :staffId
             and o.status in :activeStatuses
             and o.leaseExpiresAt is not null
             and o.leaseExpiresAt >= :now
           order by o.updatedAt desc
           """)
    List<Order> findActiveLeaseOrdersByStaff(
            @Param("staffId") Long staffId,
            @Param("activeStatuses") List<String> activeStatuses,
            @Param("now") LocalDateTime now
    );

    @Query("""
           select o from Order o
           where o.assignee.id = :staffId
             and o.status = :completedStatus
             and o.updatedAt >= :from
             and o.updatedAt < :to
           order by o.updatedAt desc
           """)
    List<Order> findCompletedOrdersByStaffAndUpdatedAtRange(
            @Param("staffId") Long staffId,
            @Param("completedStatus") String completedStatus,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
           select count(o) from Order o
           where o.assignee.id = :staffId
             and o.status = :completedStatus
             and o.updatedAt >= :from
             and o.updatedAt < :to
           """)
    long countCompletedOrdersByStaffAndUpdatedAtRange(
            @Param("staffId") Long staffId,
            @Param("completedStatus") String completedStatus,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
