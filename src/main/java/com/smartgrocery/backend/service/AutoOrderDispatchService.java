package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.OrderRepository;
import com.smartgrocery.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoOrderDispatchService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ASSIGNED = "ASSIGNED";
    private static final String STATUS_PICKING = "PICKING";
    private static final String STAFF_ROLE = "STAFF";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final int LEASE_MINUTES = 10;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional(value = "transactionManager")
    public boolean tryAutoAssign(Long orderId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<User> candidates = userRepository.findByRole_NameAndStatus(STAFF_ROLE, ACTIVE_STATUS);
        if (candidates.isEmpty()) {
            return false;
        }

        List<StaffCandidate> ranked = candidates.stream()
                .map(staff -> new StaffCandidate(
                        staff,
                        orderRepository.countActiveAssignments(staff.getId(), List.of(STATUS_ASSIGNED, STATUS_PICKING), now),
                        orderRepository.findLastAssignedAt(staff.getId())
                ))
                .sorted(Comparator
                        .comparingLong(StaffCandidate::activeLoad)
                        .thenComparing(StaffCandidate::lastAssignedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(c -> c.staff().getId()))
                .toList();

        for (StaffCandidate candidate : ranked) {
            LocalDateTime leaseExpiresAt = now.plusMinutes(LEASE_MINUTES);
            int updated = orderRepository.assignIfAvailable(
                    orderId,
                    candidate.staff().getId(),
                    leaseExpiresAt,
                    STATUS_ASSIGNED,
                    STATUS_PENDING,
                    now
            );
            if (updated > 0) {
                notifyAssignedStaff(orderId, candidate.staff());
                return true;
            }
        }
        return false;
    }

    @Transactional(value = "transactionManager")
    @Scheduled(fixedDelay = 15000)
    public void reconcileAndDispatchQueue() {
        LocalDateTime now = LocalDateTime.now(clock);
        int released = orderRepository.releaseExpiredLeases(STATUS_PENDING, List.of(STATUS_ASSIGNED, STATUS_PICKING), now);
        if (released > 0) {
            log.info("Released {} expired leases back to PENDING", released);
        }

        List<Order> queue = orderRepository.findQueueForAssignment(STATUS_PENDING, now);
        for (Order order : queue) {
            tryAutoAssign(order.getId());
        }
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

    private record StaffCandidate(User staff, long activeLoad, LocalDateTime lastAssignedAt) {}
}
