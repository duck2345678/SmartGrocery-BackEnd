package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.AttendanceRecord;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.AttendanceRecordRepository;
import com.smartgrocery.backend.repository.jpa.OrderRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDispatchServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;

    @InjectMocks private OrderDispatchService service;

    @BeforeEach
    void setupClock() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-05T08:00:00Z"), ZoneId.of("UTC"));
        service = new OrderDispatchService(orderRepository, userRepository, notificationService, attendanceRecordRepository, fixedClock);
    }

    @Test
    void tryAutoAssignReturnsFalseWhenNoCandidates() {
        when(userRepository.findByRole_NameAndStatus("STAFF", "ACTIVE")).thenReturn(List.of());
        assertFalse(service.tryAutoAssign(1L));
    }

    @Test
    void dispatchPendingOrdersNowAssignsWhenStaffAvailable() {
        User staff = User.builder().id(10L).build();
        Order order = Order.builder().id(99L).build();

        when(orderRepository.findQueueForAssignment(eq("PENDING"), any())).thenReturn(List.of(order));
        when(userRepository.findByRole_NameAndStatus("STAFF", "ACTIVE")).thenReturn(List.of(staff));
        AttendanceRecord attendanceRecord = AttendanceRecord.builder()
                .id(1L)
                .user(staff)
                .workDate(LocalDate.of(2026, 5, 5))
                .checkInAt(java.time.LocalDateTime.parse("2026-05-05T07:55:00"))
                .build();
        when(attendanceRecordRepository.findByUser_IdAndWorkDateAndCheckInAtIsNotNullAndCheckOutAtIsNull(10L, LocalDate.of(2026, 5, 5)))
                .thenReturn(Optional.of(attendanceRecord));
        when(orderRepository.countActiveAssignments(10L, List.of("ASSIGNED", "PICKING"), java.time.LocalDateTime.parse("2026-05-05T08:00:00")))
                .thenReturn(0L);
        when(orderRepository.countQueuedAssignments(10L, "QUEUED")).thenReturn(0L);
        when(orderRepository.findLastAssignedAt(10L)).thenReturn(null);
        when(orderRepository.assignIfAvailable(eq(99L), eq(10L), any(), eq("QUEUED"), eq("PENDING"), any()))
                .thenReturn(1);

        int assigned = service.dispatchPendingOrdersNow();

        assertTrue(assigned > 0);
    }
}
