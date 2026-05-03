package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AdminOpsMonitorDto;
import com.smartgrocery.backend.dto.AdminOpsOrderDto;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminOpsMonitorService {

    private static final Duration SLA_TARGET = Duration.ofMinutes(30);
    private static final Duration STALLED_THRESHOLD = Duration.ofMinutes(10);

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public AdminOpsMonitorDto monitor() {
        LocalDateTime now = LocalDateTime.now();

        List<AdminOpsOrderDto> stagnant = orderRepository.findQueueForAssignment("PENDING", now).stream()
                .filter(o -> o.getCreatedAt() != null)
                .map(o -> toDto(o, now))
                .filter(dto -> dto.getMinutesToSla() != null && dto.getMinutesToSla() <= 15)
                .limit(50)
                .toList();

        List<AdminOpsOrderDto> stalled = orderRepository.findAssignedOrders("ASSIGNED").stream()
                .filter(o -> o.getUpdatedAt() != null)
                .map(o -> toDto(o, now))
                .filter(dto -> dto.getMinutesSinceUpdate() != null && dto.getMinutesSinceUpdate() >= (int) STALLED_THRESHOLD.toMinutes())
                .sorted(Comparator.comparing(AdminOpsOrderDto::getMinutesSinceUpdate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(50)
                .toList();

        return AdminOpsMonitorDto.builder()
                .stagnantOrders(stagnant)
                .stalledStaffOrders(stalled)
                .build();
    }

    private AdminOpsOrderDto toDto(Order o, LocalDateTime now) {
        Integer minutesToSla = null;
        if (o.getCreatedAt() != null) {
            LocalDateTime due = o.getCreatedAt().plus(SLA_TARGET);
            minutesToSla = (int) Duration.between(now, due).toMinutes();
        }

        Integer minutesSinceUpdate = null;
        if (o.getUpdatedAt() != null) {
            minutesSinceUpdate = (int) Duration.between(o.getUpdatedAt(), now).toMinutes();
        }

        return AdminOpsOrderDto.builder()
                .orderId(o.getId())
                .orderNumber(o.getOrderNumber())
                .status(o.getStatus())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .assigneeId(o.getAssignee() != null ? o.getAssignee().getId() : null)
                .assigneeName(o.getAssignee() != null ? o.getAssignee().getFullName() : null)
                .leaseExpiresAt(o.getLeaseExpiresAt())
                .minutesToSla(minutesToSla)
                .minutesSinceUpdate(minutesSinceUpdate)
                .build();
    }
}

