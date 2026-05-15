package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AttendanceCheckRequest;
import com.smartgrocery.backend.entity.AttendanceRecord;
import com.smartgrocery.backend.entity.ShiftSchedule;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.AttendanceRecordRepository;
import com.smartgrocery.backend.repository.jpa.ShiftRequestRepository;
import com.smartgrocery.backend.repository.jpa.ShiftScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private ShiftScheduleRepository shiftScheduleRepository;
    @Mock private ShiftRequestRepository shiftRequestRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks private AttendanceService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "lateThresholdMinutes", 10);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "earlyBufferMinutes", 15);
    }

    private User user() {
        return User.builder().id(10L).fullName("Staff A").build();
    }

    @Test
    void checkInGShiftAcceptsSelectedBlockWithinGraceWindow() {
        LocalDate today = LocalDate.now();
        ShiftSchedule schedule = ShiftSchedule.builder()
                .user(user())
                .workDate(today)
                .shiftType("G")
                .selectedBlocks("1,4")
                .build();
        when(shiftScheduleRepository.findByUser_IdAndWorkDate(10L, today)).thenReturn(Optional.of(schedule));
        when(attendanceRecordRepository.findByUser_IdAndWorkDateAndBlockNumber(10L, today, 1)).thenReturn(Optional.empty());
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(i -> i.getArgument(0));

        AttendanceCheckRequest req = AttendanceCheckRequest.builder()
                .timestamp(LocalDateTime.of(today, java.time.LocalTime.of(6, 35)))
                .build();

        var dto = service.checkIn(user(), req);
        assertEquals("ON_TIME", dto.getCheckInStatus());
        assertEquals(1, dto.getBlockNumber());
    }

    @Test
    void checkInGShiftAllowsLateArrivalWithinBlockUntilEnd() {
        LocalDate today = LocalDate.now();
        ShiftSchedule schedule = ShiftSchedule.builder()
                .user(user())
                .workDate(today)
                .shiftType("G")
                .selectedBlocks("1,4")
                .build();
        when(shiftScheduleRepository.findByUser_IdAndWorkDate(10L, today)).thenReturn(Optional.of(schedule));
        when(attendanceRecordRepository.findByUser_IdAndWorkDateAndBlockNumber(10L, today, 1)).thenReturn(Optional.empty());
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(i -> i.getArgument(0));

        AttendanceCheckRequest req = AttendanceCheckRequest.builder()
                .timestamp(LocalDateTime.of(today, java.time.LocalTime.of(7, 0)))
                .build();

        var dto = service.checkIn(user(), req);
        assertEquals("LATE", dto.getCheckInStatus());
        assertEquals(1, dto.getBlockNumber());
    }

    @Test
    void checkInGShiftRejectsOutsideSelectedBlocks() {
        LocalDate today = LocalDate.now();
        ShiftSchedule schedule = ShiftSchedule.builder()
                .user(user())
                .workDate(today)
                .shiftType("G")
                .selectedBlocks("1,4")
                .build();
        when(shiftScheduleRepository.findByUser_IdAndWorkDate(10L, today)).thenReturn(Optional.of(schedule));

        AttendanceCheckRequest req = AttendanceCheckRequest.builder()
                .timestamp(LocalDateTime.of(today, java.time.LocalTime.of(15, 0)))
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.checkIn(user(), req));
    }
}
