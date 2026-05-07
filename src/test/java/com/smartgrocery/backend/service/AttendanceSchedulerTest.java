package com.smartgrocery.backend.service;

import com.smartgrocery.backend.repository.AttendanceRecordRepository;
import com.smartgrocery.backend.repository.UserRepository;
import com.smartgrocery.backend.scheduler.AttendanceScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceSchedulerTest {

    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private UserRepository userRepository;
    @Mock private FcmService fcmService;

    @InjectMocks private AttendanceScheduler scheduler;

    @Test
    void reminderDoesNotNotifyBeforeWindow() {
        when(attendanceRecordRepository.findByWorkDateAndCheckInAtIsNotNullAndCheckOutAtIsNull(LocalDate.now()))
                .thenReturn(List.of());

        scheduler.sendCheckoutReminders();
        verifyNoInteractions(fcmService);
    }

    @Test
    void autoCloseDoesNothingWhenNoOpenRecords() {
        when(attendanceRecordRepository.findByWorkDateAndCheckInAtIsNotNullAndCheckOutAtIsNull(LocalDate.now().minusDays(1)))
                .thenReturn(List.of());

        scheduler.autoCloseOpenShifts();
        verifyNoInteractions(fcmService);
    }
}
