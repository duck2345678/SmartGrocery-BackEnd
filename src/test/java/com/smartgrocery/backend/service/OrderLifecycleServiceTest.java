package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.AttendanceRecord;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.AttendanceRecordRepository;
import com.smartgrocery.backend.repository.jpa.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private Clock clock;

    @InjectMocks private OrderLifecycleService service;

    @Test
    void packRejectsMissingPhoto() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-05T08:00:00Z"), ZoneId.of("UTC"));
        when(clock.instant()).thenReturn(fixedClock.instant());
        when(clock.getZone()).thenReturn(fixedClock.getZone());
        User staff = User.builder().id(1L).build();
        Order order = Order.builder().id(7L).assignee(staff).status("ASSIGNED").build();
        AttendanceRecord record = AttendanceRecord.builder().checkInAt(LocalDateTime.now(fixedClock)).build();
        when(attendanceRecordRepository.findByUser_IdAndWorkDate(staff.getId(), LocalDate.of(2026, 5, 5))).thenReturn(List.of(record));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        assertThrows(IllegalArgumentException.class, () -> service.pack(7L, staff, ""));
    }

    @Test
    void completeSetsDeliveredStatus() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-05T08:00:00Z"), ZoneId.of("UTC"));
        when(clock.instant()).thenReturn(fixedClock.instant());
        when(clock.getZone()).thenReturn(fixedClock.getZone());
        User staff = User.builder().id(1L).build();
        Order order = Order.builder().id(7L).assignee(staff).status("DELIVERING").build();
        AttendanceRecord record = AttendanceRecord.builder().checkInAt(LocalDateTime.now(fixedClock)).build();
        when(attendanceRecordRepository.findByUser_IdAndWorkDate(staff.getId(), LocalDate.of(2026, 5, 5))).thenReturn(List.of(record));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var updated = service.complete(7L, staff);

        assertEquals("DELIVERED", updated.getStatus());
    }
}
