package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.*;
import com.smartgrocery.backend.entity.AttendanceRecord;
import com.smartgrocery.backend.entity.ShiftSchedule;
import com.smartgrocery.backend.entity.ShiftRequest;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.AttendanceRecordRepository;
import com.smartgrocery.backend.repository.jpa.ShiftRequestRepository;
import com.smartgrocery.backend.repository.jpa.ShiftScheduleRepository;
import com.smartgrocery.backend.repository.jpa.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ShiftScheduleRepository shiftScheduleRepository;
    private final ShiftRequestRepository shiftRequestRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${app.attendance.early-buffer-minutes:15}")
    private int earlyBufferMinutes;

    @Value("${app.attendance.late-threshold-minutes:10}")
    private int lateThresholdMinutes;

    private static final List<ShiftConfigDto.ShiftBlock> STANDARD_BLOCKS = List.of(
            new ShiftConfigDto.ShiftBlock(1, LocalTime.of(6, 30), LocalTime.of(10, 30)),
            new ShiftConfigDto.ShiftBlock(2, LocalTime.of(10, 30), LocalTime.of(14, 30)),
            new ShiftConfigDto.ShiftBlock(3, LocalTime.of(14, 30), LocalTime.of(18, 30)),
            new ShiftConfigDto.ShiftBlock(4, LocalTime.of(18, 30), LocalTime.of(22, 30))
    );

    private static final Map<String, List<ShiftConfigDto.ShiftBlock>> SHIFT_CONFIG = Map.of(
            "S", List.of(new ShiftConfigDto.ShiftBlock(1, LocalTime.of(6, 30), LocalTime.of(14, 30))),
            "C", List.of(new ShiftConfigDto.ShiftBlock(1, LocalTime.of(14, 30), LocalTime.of(22, 30))),
            "G", STANDARD_BLOCKS
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

        ShiftSchedule schedule = shiftScheduleRepository.findByUser_IdAndWorkDate(user.getId(), today)
                .orElseThrow(() -> new IllegalArgumentException("Không có lịch làm việc hôm nay."));

        String normalizedScheduleType = normalizeScheduleShiftType(schedule.getShiftType());
        if ("OFF".equals(normalizedScheduleType) || "P".equals(normalizedScheduleType)) {
             throw new IllegalArgumentException("Ca làm việc hôm nay không yêu cầu chấm công.");
        }

        List<ShiftConfigDto.ShiftBlock> blocks = SHIFT_CONFIG.get(normalizedScheduleType);
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalStateException("Cấu hình ca làm lỗi.");
        }

        int targetBlock = 1;
        ShiftConfigDto.ShiftBlock currentBlockConfig = blocks.get(0);

        if ("G".equals(normalizedScheduleType)) {
            List<Integer> selectedBlocks = parseSelectedBlocks(schedule.getSelectedBlocks());
            if (selectedBlocks.isEmpty()) {
                throw new IllegalStateException("Ca G chưa được cấu hình block.");
            }

            boolean withinAnyBlockWindow = false;
            ShiftConfigDto.ShiftBlock matchedBlock = null;
            Integer matchedBlockNumber = null;
            for (Integer blockNumber : selectedBlocks) {
                ShiftConfigDto.ShiftBlock candidate = findBlockByNumber(blocks, blockNumber);
                if (candidate == null) continue;
                LocalTime allowedStart = candidate.getStartTime().minusMinutes(earlyBufferMinutes);
                LocalTime allowedLateEnd = candidate.getStartTime().plusMinutes(lateThresholdMinutes);
                LocalTime allowedEnd = candidate.getEndTime();
                if (!nowTime.isBefore(allowedStart) && !nowTime.isAfter(allowedEnd)) {
                    withinAnyBlockWindow = true;
                }
                if (!nowTime.isBefore(allowedStart) && !nowTime.isAfter(allowedLateEnd)) {
                    matchedBlock = candidate;
                    matchedBlockNumber = candidate.getBlockNumber();
                }
            }

            if (!withinAnyBlockWindow) {
                throw new IllegalArgumentException("Chưa tới giờ vào ca hoặc đã quá khung giờ được đăng ký.");
            }

            if (matchedBlock == null) {
                matchedBlock = selectedBlocks.stream()
                        .map(blockNumber -> findBlockByNumber(blocks, blockNumber))
                        .filter(Objects::nonNull)
                        .filter(candidate -> !nowTime.isBefore(candidate.getStartTime().minusMinutes(earlyBufferMinutes)) && !nowTime.isAfter(candidate.getEndTime()))
                        .findFirst()
                        .orElse(null);
                if (matchedBlock == null) {
                    throw new IllegalArgumentException("Chưa tới giờ vào ca hoặc không đúng khung giờ được đăng ký.");
                }
                matchedBlockNumber = matchedBlock.getBlockNumber();
            }

            currentBlockConfig = matchedBlock;
            targetBlock = matchedBlockNumber != null ? matchedBlockNumber : targetBlock;
        }

        Optional<AttendanceRecord> existingOpt = attendanceRecordRepository.findByUser_IdAndWorkDateAndBlockNumber(user.getId(), today, targetBlock);
        if (existingOpt.isPresent() && existingOpt.get().getCheckInAt() != null) {
            throw new IllegalArgumentException("Bạn đã vào ca rồi.");
        }

        AttendanceRecord record = existingOpt.orElse(
                AttendanceRecord.builder()
                        .user(user)
                        .workDate(today)
                        .shiftType(normalizedScheduleType)
                        .blockNumber(targetBlock)
                        .build()
        );

        record.setCheckInAt(req.getTimestamp());
        record.setLatitude(req.getLatitude());
        record.setLongitude(req.getLongitude());
        record.setNote(req.getNote());

        LocalTime allowedStart = currentBlockConfig.getStartTime().minusMinutes(earlyBufferMinutes);
        LocalTime lateStart = currentBlockConfig.getStartTime().plusMinutes(lateThresholdMinutes);
        if (nowTime.isBefore(allowedStart) || nowTime.isAfter(currentBlockConfig.getEndTime())) {
            throw new IllegalArgumentException("Chưa tới giờ vào ca hoặc đã quá khung giờ vào ca.");
        }
        if (nowTime.isAfter(lateStart)) {
            record.setCheckInStatus("LATE");
        } else {
            record.setCheckInStatus("ON_TIME");
        }

        AttendanceRecord saved = attendanceRecordRepository.save(record);
        applicationEventPublisher.publishEvent(new StaffCheckedInEvent(user.getId()));
        return mapToDto(saved);
    }

    @Transactional
    public AttendanceRecordDto checkOut(User user, AttendanceCheckRequest req) {
        LocalDate today = req.getTimestamp().toLocalDate();
        LocalTime nowTime = req.getTimestamp().toLocalTime();

        List<AttendanceRecord> todayRecords = attendanceRecordRepository.findByUser_IdAndWorkDate(user.getId(), today);
        if (todayRecords.isEmpty()) {
            throw new IllegalArgumentException("Bạn chưa vào ca.");
        }

        // Tìm record chưa checkout (có checkInAt != null và checkOutAt == null)
        AttendanceRecord currentRecord = todayRecords.stream()
                .filter(r -> r.getCheckInAt() != null && r.getCheckOutAt() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca làm việc đang mở."));

        // Release any active orders in preparation for this staff
        orderRepository.releaseAllAssignmentsForStaff(
                user.getId(),
                "PENDING",
                List.of("ASSIGNED", "PICKING")
        );

        List<ShiftConfigDto.ShiftBlock> blocks = SHIFT_CONFIG.get(currentRecord.getShiftType());
        ShiftConfigDto.ShiftBlock currentBlockConfig = findBlockByNumber(blocks, currentRecord.getBlockNumber());
        if (currentBlockConfig == null) {
            currentBlockConfig = blocks.get(0);
        }

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
        Optional<ShiftSchedule> scheduleOpt = shiftScheduleRepository.findByUser_IdAndWorkDate(user.getId(), today);
        List<AttendanceRecord> records = attendanceRecordRepository.findByUser_IdAndWorkDate(user.getId(), today);

        return StaffAttendanceTodayDto.builder()
                .date(today)
                .shiftType(scheduleOpt.map(s -> normalizeScheduleShiftType(s.getShiftType())).orElse("OFF"))
                .records(records.stream().map(this::mapToDto).collect(Collectors.toList()))
                .build();
    }

    public List<AttendanceDayDto> getMonthlyCalendar(User user, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        LocalDate today = LocalDate.now();

        List<ShiftSchedule> schedules = shiftScheduleRepository.findByUser_IdAndWorkDateBetween(user.getId(), start, end);
        List<AttendanceRecord> records = attendanceRecordRepository.findByUser_IdAndWorkDateBetween(user.getId(), start, end);
        List<ShiftRequest> requests = shiftRequestRepository.findByUser_IdAndWorkDateBetween(user.getId(), start, end);

        Map<LocalDate, ShiftSchedule> scheduleMap = schedules.stream().collect(Collectors.toMap(ShiftSchedule::getWorkDate, s -> s));
        Map<LocalDate, List<AttendanceRecord>> recordMap = records.stream().collect(Collectors.groupingBy(AttendanceRecord::getWorkDate));
        Map<LocalDate, ShiftRequest> requestMap = requests.stream().collect(Collectors.toMap(ShiftRequest::getWorkDate, r -> r, (a, b) -> a));

        List<AttendanceDayDto> result = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            ShiftSchedule schedule = scheduleMap.get(date);
            List<AttendanceRecord> dayRecords = recordMap.getOrDefault(date, Collections.emptyList());

            boolean hasSchedule = schedule != null;
            String shiftType = schedule != null ? normalizeScheduleShiftType(schedule.getShiftType()) : "OFF";
            String selectedBlocks = schedule != null ? (schedule.getSelectedBlocks() != null ? schedule.getSelectedBlocks() : "") : "";
            String dayStatus = determineDayStatus(date, hasSchedule, shiftType, dayRecords, today);
            ShiftRequest req = requestMap.get(date);

            result.add(AttendanceDayDto.builder()
                    .date(date)
                    .shiftType(shiftType)
                    .dayStatus(dayStatus)
                    .records(dayRecords.stream().map(this::mapToDto).collect(Collectors.toList()))
                    .requestId(req != null ? req.getId() : null)
                    .requestShiftType(req != null ? req.getShiftType() : null)
                    .requestStatus(req != null ? req.getStatus() : null)
                    .requestAdminNote(req != null ? req.getAdminNote() : null)
                    .selectedBlocks(selectedBlocks)
                    .build());
        }

        return result;
    }

    private String determineDayStatus(LocalDate date, boolean hasSchedule, String shiftType, List<AttendanceRecord> records, LocalDate today) {
        if (!hasSchedule) return "NO_SCHEDULE";
        if ("OFF".equals(shiftType) || "P".equals(shiftType)) return "OFF";

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
            "LATE".equals(r.getCheckInStatus()) || "EARLY".equals(r.getCheckOutStatus()) || "LATE".equals(r.getCheckOutStatus())
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

    private String normalizeScheduleShiftType(String shiftType) {
        String s = String.valueOf(shiftType == null ? "" : shiftType).trim().toUpperCase();
        if (s.equals("S") || s.equals("C") || s.equals("G") || s.equals("P") || s.equals("OFF")) return s;
        return "OFF";
    }

    private List<Integer> parseSelectedBlocks(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toList());
    }

    private ShiftConfigDto.ShiftBlock findBlockByNumber(List<ShiftConfigDto.ShiftBlock> blocks, Integer blockNumber) {
        if (blocks == null || blockNumber == null) return null;
        return blocks.stream()
                .filter(b -> Objects.equals(b.getBlockNumber(), blockNumber))
                .findFirst()
                .orElse(null);
    }

    /**
     * Tự động đóng tất cả ca làm việc còn mở vào cuối ngày.
     * Chạy 1 lần duy nhất lúc 23:59 mỗi ngày.
     * - Giờ đóng ca = giờ hệ thống thực tế (thời điểm chạy cron).
     * - Trạng thái = LATE (ghi nhận về trễ / quên bấm ra ca).
     */
    @Scheduled(cron = "0 59 23 * * *")
    @Transactional
    public void autoCloseExpiredShifts() {
        LocalDateTime now = LocalDateTime.now();

        List<AttendanceRecord> openRecords = attendanceRecordRepository.findByCheckOutAtIsNull();
        int closedCount = 0;

        for (AttendanceRecord record : openRecords) {
            record.setCheckOutAt(now);
            record.setCheckOutStatus("LATE");

            String autoNote = "[Hệ thống tự động đóng ca lúc " + now.toLocalTime().withNano(0) + " — nhân viên quên bấm ra ca]";
            record.setNote(record.getNote() == null ? autoNote : record.getNote() + "\n" + autoNote);

            if (record.getUser() != null && record.getUser().getId() != null) {
                orderRepository.releaseAllAssignmentsForStaff(
                        record.getUser().getId(),
                        "PENDING",
                        List.of("ASSIGNED", "PICKING")
                );
            }

            attendanceRecordRepository.save(record);
            closedCount++;
        }

        if (closedCount > 0) {
            log.info("Attendance: Cuối ngày — tự động đóng {} ca làm việc, ghi nhận về trễ.", closedCount);
        }
    }
}
