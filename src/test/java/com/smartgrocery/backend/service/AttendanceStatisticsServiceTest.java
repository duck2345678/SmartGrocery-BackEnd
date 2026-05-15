package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.AttendanceRecord;
import com.smartgrocery.backend.entity.ShiftSchedule;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.AttendanceRecordRepository;
import com.smartgrocery.backend.repository.jpa.ShiftRequestRepository;
import com.smartgrocery.backend.repository.jpa.ShiftScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AttendanceStatisticsServiceTest {

    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private ShiftScheduleRepository shiftScheduleRepository;
    @Mock private ShiftRequestRepository shiftRequestRepository;

    @InjectMocks private AttendanceStatisticsService service;

    @Test
    void monthlyStatsCountsAbsenceAndLateCorrectly() {
        User user = User.builder().id(1L).build();
        LocalDate start = LocalDate.of(2026, 5, 1);
        ShiftSchedule schedule = ShiftSchedule.builder()
                .user(user)
                .workDate(start)
                .shiftType("G")
                .selectedBlocks("1,4")
                .build();
        AttendanceRecord rec = AttendanceRecord.builder()
                .user(user)
                .workDate(start)
                .shiftType("G")
                .blockNumber(1)
                .checkInAt(LocalDateTime.of(2026, 5, 1, 6, 50))
                .checkOutAt(LocalDateTime.of(2026, 5, 1, 10, 30))
                .checkInStatus("LATE")
                .checkOutStatus("ON_TIME")
                .build();

        lenient().when(shiftScheduleRepository.findByUser_IdAndWorkDateBetween(1L, start, start.withDayOfMonth(31))).thenReturn(List.of(schedule));
        lenient().when(attendanceRecordRepository.findByUser_IdAndWorkDateBetween(1L, start, start.withDayOfMonth(31))).thenReturn(List.of(rec));
        lenient().when(shiftRequestRepository.findByUser_IdAndWorkDateBetween(1L, start, start.withDayOfMonth(31))).thenReturn(List.of());

        var dto = service.getMonthlyStats(user, 2026, 5);
        assertEquals(1, dto.getScheduledDays());
        assertEquals(1, dto.getAttendedDays());
        assertEquals(1, dto.getLateCheckIns());
        assertEquals(0, dto.getAbsentDays());
    }

    @Test
    void monthlyStatsHandlesNoScheduleMonth() {
        User user = User.builder().id(2L).build();
        LocalDate start = LocalDate.of(2026, 5, 1);
        lenient().when(shiftScheduleRepository.findByUser_IdAndWorkDateBetween(2L, start, start.withDayOfMonth(31))).thenReturn(List.of());
        lenient().when(attendanceRecordRepository.findByUser_IdAndWorkDateBetween(2L, start, start.withDayOfMonth(31))).thenReturn(List.of());
        lenient().when(shiftRequestRepository.findByUser_IdAndWorkDateBetween(2L, start, start.withDayOfMonth(31))).thenReturn(List.of());

        var dto = service.getMonthlyStats(user, 2026, 5);
        assertEquals(0, dto.getScheduledDays());
        assertEquals(0, dto.getAttendedDays());
        assertEquals(0, dto.getTotalBlocks());
    }
}
