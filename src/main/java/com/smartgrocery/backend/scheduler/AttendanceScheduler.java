package com.smartgrocery.backend.scheduler;

import com.smartgrocery.backend.entity.AttendanceRecord;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.AttendanceRecordRepository;
import com.smartgrocery.backend.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceScheduler {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final FcmService fcmService;

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void sendCheckoutReminders() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        List<AttendanceRecord> openRecords = attendanceRecordRepository
                .findByWorkDateAndCheckInAtIsNotNullAndCheckOutAtIsNull(today);

        for (AttendanceRecord record : openRecords) {
            LocalTime endTime = resolveEndTime(record.getShiftType(), record.getBlockNumber());
            if (endTime == null) continue;
            LocalTime reminderAt = endTime.plusMinutes(15);
            LocalTime reminderUntil = reminderAt.plusMinutes(5);
            if (now.isBefore(reminderAt) || now.isAfter(reminderUntil)) continue;

            User user = record.getUser();
            if (user != null && user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
                String body = "Ca làm ngày " + today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " đã gần kết thúc, vui lòng đóng ca.";
                fcmService.sendPushNotification(user.getFcmToken(), "Nhắc đóng ca", body);
            }
        }
    }

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void autoCloseOpenShifts() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<AttendanceRecord> openRecords = attendanceRecordRepository
                .findByWorkDateAndCheckInAtIsNotNullAndCheckOutAtIsNull(yesterday);

        for (AttendanceRecord record : openRecords) {
            LocalTime endTime = resolveEndTime(record.getShiftType(), record.getBlockNumber());
            if (endTime == null) continue;

            record.setCheckOutAt(LocalDateTime.of(record.getWorkDate(), endTime));
            record.setCheckOutStatus("AUTO_CLOSED");
            attendanceRecordRepository.save(record);

            User user = record.getUser();
            if (user != null && user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
                String body = "Ca làm ngày " + record.getWorkDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " đã bị tự động đóng vì bạn quên ra ca.";
                fcmService.sendPushNotification(user.getFcmToken(), "Tự động đóng ca", body);
            }
        }
    }

    private LocalTime resolveEndTime(String shiftType, Integer blockNumber) {
        if (shiftType == null) return null;
        return switch (shiftType.toUpperCase()) {
            case "S" -> LocalTime.of(14, 30);
            case "C" -> LocalTime.of(22, 30);
            case "G" -> switch (blockNumber == null ? 0 : blockNumber) {
                case 1 -> LocalTime.of(10, 30);
                case 2 -> LocalTime.of(14, 30);
                case 3 -> LocalTime.of(18, 30);
                case 4 -> LocalTime.of(22, 30);
                default -> null;
            };
            default -> null;
        };
    }
}
