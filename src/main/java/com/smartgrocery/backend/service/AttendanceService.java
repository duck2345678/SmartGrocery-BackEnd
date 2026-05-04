package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.*;
import com.smartgrocery.backend.entity.AttendanceRecord;
import com.smartgrocery.backend.entity.ShiftSchedule;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.AttendanceRecordRepository;
import com.smartgrocery.backend.repository.ShiftScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ShiftScheduleRepository shiftScheduleRepository;

    @Value("${app.attendance.grace-period-minutes:5}")
    private int gracePeriodMinutes;

    private static final Map<String, List<ShiftConfigDto.ShiftBlock>> SHIFT_CONFIG = Map.of(
            "S", List.of(new ShiftConfigDto.ShiftBlock(1, LocalTime.of(6, 30), LocalTime.of(14, 30))),
            "C", List.of(new ShiftConfigDto.ShiftBlock(1, LocalTime.of(14, 30), LocalTime.of(22, 30))),
            "G", List.of(
                    new ShiftConfigDto.ShiftBlock(1, LocalTime.of(6, 30), LocalTime.of(10, 30)),
                    new ShiftConfigDto.ShiftBlock(2, LocalTime.of(18, 30), LocalTime.of(22, 30))
            )
    );

    public List<ShiftConfigDto> getShiftConfig() {
        return SHIFT_CONFIG.entrySet().stream()
                .map(e -> new ShiftConfigDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Transactional
    public AttendanceRecordDto checkIn(User user, AttendanceCheckRequest req) {
        LocalDate today = req.getTimestamp().toLocalDate();
        LocalTime nowTime = req.getTimestamp().toLocalTime();

        ShiftSchedule schedule = shiftScheduleRepository.findByUserIdAndWorkDate(user.getId(), today)
                .orElseThrow(() -> new IllegalArgumentException("Không có lịch làm việc hôm nay."));

        if ("OFF".equals(schedule.getShiftType()) || "P".equals(schedule.getShiftType()) || "F".equals(schedule.getShiftType())) {
             throw new IllegalArgumentException("Ca làm việc hôm nay không yêu cầu chấm công.");
        }

        List<ShiftConfigDto.ShiftBlock> blocks = SHIFT_CONFIG.get(schedule.getShiftType());
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalStateException("Cấu hình ca làm lỗi.");
        }

        int targetBlock = 1;
        ShiftConfigDto.ShiftBlock currentBlockConfig = blocks.get(0);

        if ("G".equals(schedule.getShiftType())) {
            // Xác định block dựa vào thời gian hiện tại
            // Nếu là buổi sáng (trước 14:00) thì là block 1, ngược lại block 2
            if (nowTime.isAfter(LocalTime.of(14, 0))) {
                targetBlock = 2;
                currentBlockConfig = blocks.get(1);
            }
        }

        Optional<AttendanceRecord> existingOpt = attendanceRecordRepository.findByUserIdAndWorkDateAndBlockNumber(user.getId(), today, targetBlock);
        if (existingOpt.isPresent() && existingOpt.get().getCheckInAt() != null) {
            throw new IllegalArgumentException("Bạn đã vào ca rồi.");
        }

        AttendanceRecord record = existingOpt.orElse(
                AttendanceRecord.builder()
                        .user(user)
                        .workDate(today)
                        .shiftType(schedule.getShiftType())
                        .blockNumber(targetBlock)
                        .build()
        );

        record.setCheckInAt(req.getTimestamp());
        record.setLatitude(req.getLatitude());
        record.setLongitude(req.getLongitude());
        record.setNote(req.getNote());

        LocalTime allowedStart = currentBlockConfig.getStartTime().plusMinutes(gracePeriodMinutes);
        if (nowTime.isAfter(allowedStart)) {
            record.setCheckInStatus("LATE");
        } else {
            record.setCheckInStatus("ON_TIME");
        }

        return mapToDto(attendanceRecordRepository.save(record));
    }

    @Transactional
    public AttendanceRecordDto checkOut(User user, AttendanceCheckRequest req) {
        LocalDate today = req.getTimestamp().toLocalDate();
        LocalTime nowTime = req.getTimestamp().toLocalTime();

        List<AttendanceRecord> todayRecords = attendanceRecordRepository.findByUserIdAndWorkDate(user.getId(), today);
        if (todayRecords.isEmpty()) {
            throw new IllegalArgumentException("Bạn chưa vào ca.");
        }

        // Tìm record chưa checkout (có checkInAt != null và checkOutAt == null)
        AttendanceRecord currentRecord = todayRecords.stream()
                .filter(r -> r.getCheckInAt() != null && r.getCheckOutAt() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca làm việc đang mở."));

        List<ShiftConfigDto.ShiftBlock> blocks = SHIFT_CONFIG.get(currentRecord.getShiftType());
        ShiftConfigDto.ShiftBlock currentBlockConfig = blocks.stream()
                .filter(b -> b.getBlockNumber() == currentRecord.getBlockNumber())
                .findFirst()
                .orElse(blocks.get(0));

        currentRecord.setCheckOutAt(req.getTimestamp());
        if (req.getNote() != null && !req.getNote().isEmpty()) {
            String existingNote = currentRecord.getNote() != null ? currentRecord.getNote() + "\n" : "";
            currentRecord.setNote(existingNote + req.getNote());
        }

        if (nowTime.isBefore(currentBlockConfig.getEndTime())) {
            currentRecord.setCheckOutStatus("EARLY");
        } else {
            currentRecord.setCheckOutStatus("ON_TIME");
        }

        return mapToDto(attendanceRecordRepository.save(currentRecord));
    }

    public StaffAttendanceTodayDto getTodayStatus(User user) {
        LocalDate today = LocalDate.now();
        Optional<ShiftSchedule> scheduleOpt = shiftScheduleRepository.findByUserIdAndWorkDate(user.getId(), today);
        List<AttendanceRecord> records = attendanceRecordRepository.findByUserIdAndWorkDate(user.getId(), today);

        return StaffAttendanceTodayDto.builder()
                .date(today)
                .shiftType(scheduleOpt.map(ShiftSchedule::getShiftType).orElse(null))
                .records(records.stream().map(this::mapToDto).collect(Collectors.toList()))
                .build();
    }

    public List<AttendanceDayDto> getMonthlyCalendar(User user, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        LocalDate today = LocalDate.now();

        List<ShiftSchedule> schedules = shiftScheduleRepository.findByUserIdAndWorkDateBetween(user.getId(), start, end);
        List<AttendanceRecord> records = attendanceRecordRepository.findByUserIdAndWorkDateBetween(user.getId(), start, end);

        Map<LocalDate, ShiftSchedule> scheduleMap = schedules.stream().collect(Collectors.toMap(ShiftSchedule::getWorkDate, s -> s));
        Map<LocalDate, List<AttendanceRecord>> recordMap = records.stream().collect(Collectors.groupingBy(AttendanceRecord::getWorkDate));

        List<AttendanceDayDto> result = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            ShiftSchedule schedule = scheduleMap.get(date);
            List<AttendanceRecord> dayRecords = recordMap.getOrDefault(date, Collections.emptyList());
            
            String shiftType = schedule != null ? schedule.getShiftType() : null;
            String dayStatus = determineDayStatus(date, shiftType, dayRecords, today);

            result.add(AttendanceDayDto.builder()
                    .date(date)
                    .shiftType(shiftType)
                    .dayStatus(dayStatus)
                    .records(dayRecords.stream().map(this::mapToDto).collect(Collectors.toList()))
                    .build());
        }

        return result;
    }

    private String determineDayStatus(LocalDate date, String shiftType, List<AttendanceRecord> records, LocalDate today) {
        if (shiftType == null) return "OFF"; // Không phân ca
        if ("OFF".equals(shiftType)) return "OFF";

        boolean isFuture = date.isAfter(today);
        if (isFuture) return "SCHEDULED";
        
        // Hiện tại/quá khứ mà chưa có records nào
        if (records.isEmpty()) {
             if (date.isBefore(today)) return "ABSENT";
             return "SCHEDULED"; // Vẫn trong ngày hôm nay nhưng chưa tới giờ làm
        }

        int expectedBlocks = "G".equals(shiftType) ? 2 : 1;
        boolean hasMissing = records.size() < expectedBlocks;
        boolean hasIncomplete = records.stream().anyMatch(r -> r.getCheckOutAt() == null);
        boolean hasLateOrEarly = records.stream().anyMatch(r -> 
            "LATE".equals(r.getCheckInStatus()) || "EARLY".equals(r.getCheckOutStatus())
        );

        if (date.isBefore(today)) {
            if (hasMissing || hasIncomplete || hasLateOrEarly) return "LATE"; // Cam - có vi phạm
            return "ON_TIME"; // Xanh lá
        } else {
            // For today
            if (hasLateOrEarly) return "LATE";
            if (hasMissing || hasIncomplete) return "SCHEDULED"; // Đang làm việc hoặc chờ làm block 2
            return "ON_TIME"; // Xanh lá - đã xong hết cho hôm nay
        }
    }

    private AttendanceRecordDto mapToDto(AttendanceRecord r) {
        return AttendanceRecordDto.builder()
                .id(r.getId())
                .workDate(r.getWorkDate())
                .shiftType(r.getShiftType())
                .blockNumber(r.getBlockNumber())
                .checkInAt(r.getCheckInAt())
                .checkOutAt(r.getCheckOutAt())
                .checkInStatus(r.getCheckInStatus())
                .checkOutStatus(r.getCheckOutStatus())
                .note(r.getNote())
                .build();
    }
}
