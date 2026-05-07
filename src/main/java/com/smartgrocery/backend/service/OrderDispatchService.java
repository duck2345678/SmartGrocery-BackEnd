package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.AttendanceRecordRepository;
import com.smartgrocery.backend.repository.OrderRepository;
import com.smartgrocery.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDispatchService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ASSIGNED = "ASSIGNED";
    private static final String STATUS_PICKING = "PICKING";
    private static final String STAFF_ROLE = "STAFF";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final int LEASE_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final Clock clock;

    @Transactional(value = "transactionManager")
    public boolean tryAutoAssign(Long orderId) {
        LocalDateTime now = LocalDateTime.now(clock);
        var staff = pickBestStaff(now);
        if (staff == null) {
            return false;
        }
        return assignToQueue(orderId, staff, now);
    }

    @Transactional(value = "transactionManager")
    public int dispatchPendingOrdersNow() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Order> queue = orderRepository.findQueueForAssignment(STATUS_PENDING, now);
        int assigned = 0;
        for (Order order : queue) {
            var staff = pickBestStaff(now);
            if (staff != null && assignToQueue(order.getId(), staff, now)) {
                assigned++;
            }
        }
        return assigned;
    }

    private User pickBestStaff(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<User> candidates = userRepository.findByRole_NameAndStatus(STAFF_ROLE, ACTIVE_STATUS).stream()
                .filter(staff -> attendanceRecordRepository
                        .findByUser_IdAndWorkDateAndCheckInAtIsNotNullAndCheckOutAtIsNull(staff.getId(), today)
                        .isPresent())
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.stream()
                .map(staff -> new StaffCandidate(
                        staff,
                        orderRepository.countActiveAssignments(staff.getId(), List.of(STATUS_ASSIGNED, STATUS_PICKING), now),
                        orderRepository.countQueuedAssignments(staff.getId(), STATUS_QUEUED),  // Check if staff already has 1 queued backup order
                        orderRepository.findLastAssignedAt(staff.getId())
                ))
                .filter(c -> c.queuedCount() == 0)  // Only staff without a queued backup order
                .sorted(Comparator
                        .comparingLong(StaffCandidate::activeLoad)
                        .thenComparing(StaffCandidate::lastAssignedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(c -> c.staff().getId()))
                .map(StaffCandidate::staff)
                .findFirst()
                .orElse(null);
    }

    private static final String STATUS_QUEUED = "QUEUED";

    private boolean assignToQueue(Long orderId, User staff, LocalDateTime now) {
        LocalDateTime leaseExpiresAt = now.plusMinutes(LEASE_MINUTES);
        int updated = orderRepository.assignIfAvailable(
                orderId,
                staff.getId(),
                leaseExpiresAt,
                STATUS_QUEUED,
                STATUS_PENDING,
                now
        );
        if (updated > 0) {
            orderRepository.touchAssignedAt(orderId, now);
            notifyAssignedStaff(orderId, staff);
            return true;
        }
        return false;
    }

    private void notifyAssignedStaff(Long orderId, User staff) {
        try {
            notificationService.notifyStaff(
                    "Đơn hàng mới đã phân công",
                    "Bạn có đơn mới #" + orderId + ", vui lòng vào app staff để xử lý.",
                    "NEW_ORDER_ASSIGNED",
                    Map.of("route", "/(staff)/lease-queue", "type", "NEW_ORDER_ASSIGNED"),
                    List.of(staff)
            );
        } catch (Exception e) {
            log.warn("Could not notify assigned staff {} for order {}: {}", staff.getId(), orderId, e.getMessage());
        }
    }

    private static final class StaffCandidate {
        private final User staff;
        private final long activeLoad;
        private final long queuedCount;
        private final LocalDateTime lastAssignedAt;

        private StaffCandidate(User staff, long activeLoad, long queuedCount, LocalDateTime lastAssignedAt) {
            this.staff = staff;
            this.activeLoad = activeLoad;
            this.queuedCount = queuedCount;
            this.lastAssignedAt = lastAssignedAt;
        }

        private User staff() {
            return staff;
        }

        private long activeLoad() {
            return activeLoad;
        }

        private long queuedCount() {
            return queuedCount;
        }

        private LocalDateTime lastAssignedAt() {
            return lastAssignedAt;
        }
    }
}
