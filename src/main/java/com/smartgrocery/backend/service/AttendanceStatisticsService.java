package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.*;
import com.smartgrocery.backend.entity.AttendanceRecord;
import com.smartgrocery.backend.entity.ShiftSchedule;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.AttendanceRecordRepository;
import com.smartgrocery.backend.repository.jpa.ShiftScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceStatisticsService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ShiftScheduleRepository shiftScheduleRepository;

    private static final Map<String, List<ShiftConfigDto.ShiftBlock>> SHIFT_CONFIG = Map.of(
            "S", List.of(new ShiftConfigDto.ShiftBlock(1, LocalTime.of(6, 30), LocalTime.of(14, 30))),
            "C", List.of(new ShiftConfigDto.ShiftBlock(1, LocalTime.of(14, 30), LocalTime.of(22, 30))),
            "G", List.of(
                    new ShiftConfigDto.ShiftBlock(1, LocalTime.of(6, 30), LocalTime.of(10, 30)),
                    new ShiftConfigDto.ShiftBlock(2, LocalTime.of(10, 30), LocalTime.of(14, 30)),
                    new ShiftConfigDto.ShiftBlock(3, LocalTime.of(14, 30), LocalTime.of(18, 30)),
                    new ShiftConfigDto.ShiftBlock(4, LocalTime.of(18, 30), LocalTime.of(22, 30))
            )
    );

    @Transactional(readOnly = true)
    public AttendanceMonthlyStatsDto getMonthlyStats(User user, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        LocalDate today = LocalDate.now();

        List<ShiftSchedule> schedules = shiftScheduleRepository.findByUser_IdAndWorkDateBetween(user.getId(), start, end);
        List<AttendanceRecord> records = attendanceRecordRepository.findByUser_IdAndWorkDateBetween(user.getId(), start, end);

        Map<LocalDate, ShiftSchedule> scheduleMap = schedules.stream().collect(Collectors.toMap(ShiftSchedule::getWorkDate, s -> s, (a, b) -> a));
        Map<LocalDate, List<AttendanceRecord>> recordMap = records.stream().collect(Collectors.groupingBy(AttendanceRecord::getWorkDate));

        int scheduledDays = 0, attendedDays = 0, absentDays = 0, lateCheckIns = 0, earlyCheckOuts = 0;
        int onTimeCheckIns = 0, onTimeCheckOuts = 0, totalBlocks = 0, completedBlocks = 0;
        long totalWorkedMinutes = 0, totalScheduledMinutes = 0, lateMinutes = 0, earlyMinutes = 0;

        List<AttendanceDailySummaryDto> dailySummaries = new ArrayList<>();
        List<AttendanceChartPointDto> chartPoints = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            ShiftSchedule schedule = scheduleMap.get(date);
            List<AttendanceRecord> dayRecords = recordMap.getOrDefault(date, Collections.emptyList());
            String shiftType = schedule != null ? normalize(schedule.getShiftType()) : "OFF";
            boolean countable = schedule != null && Set.of("S", "C", "G").contains(shiftType) && (schedule.getSelectedBlocks() != null || "S".equals(shiftType) || "C".equals(shiftType));
            String dayStatus = determineDayStatus(date, schedule != null, shiftType, dayRecords, today);

            int scheduledBlocksForDay = (countable && schedule != null) ? ("G".equals(shiftType) ? parseSelectedBlocks(schedule.getSelectedBlocks()).size() : 1) : 0;
            scheduledDays += countable ? 1 : 0;
            totalBlocks += scheduledBlocksForDay;

            int completedBlocksForDay = 0;
            boolean hasLate = false;
            boolean hasEarly = false;
            long workedMinutesForDay = 0;
            long scheduledMinutesForDay = 0;

            if (countable && !dayRecords.isEmpty()) {
                attendedDays++;
                for (AttendanceRecord record : dayRecords) {
                    ShiftConfigDto.ShiftBlock block = resolveBlock(shiftType, record.getBlockNumber());
                    if (block == null) continue;
                    scheduledMinutesForDay += Duration.between(block.getStartTime(), block.getEndTime()).toMinutes();

                    if ("LATE".equals(record.getCheckInStatus())) lateCheckIns++;
                    if ("ON_TIME".equals(record.getCheckInStatus())) onTimeCheckIns++;
                    if ("EARLY".equals(record.getCheckOutStatus())) earlyCheckOuts++;
                    if ("ON_TIME".equals(record.getCheckOutStatus()) || "AUTO_CLOSED".equals(record.getCheckOutStatus())) onTimeCheckOuts++;

                    if (record.getCheckInAt() != null && record.getCheckOutAt() != null) {
                        completedBlocksForDay++;
                        workedMinutesForDay += Duration.between(record.getCheckInAt(), record.getCheckOutAt()).toMinutes();
                    }
                    if ("LATE".equals(record.getCheckInStatus())) {
                        hasLate = true;
                        lateMinutes += computeLateMinutes(block, record.getCheckInAt());
                    }
                    if ("EARLY".equals(record.getCheckOutStatus())) {
                        hasEarly = true;
                        earlyMinutes += computeEarlyMinutes(block, record.getCheckOutAt());
                    }
                }
            }

            if (countable && dayRecords.isEmpty() && date.isBefore(today)) absentDays++;
            completedBlocks += completedBlocksForDay;
            totalWorkedMinutes += workedMinutesForDay;
            totalScheduledMinutes += scheduledMinutesForDay;

            dailySummaries.add(AttendanceDailySummaryDto.builder()
                    .date(date)
                    .shiftType(shiftType)
                    .dayStatus(dayStatus)
                    .scheduledBlocks(scheduledBlocksForDay)
                    .completedBlocks(completedBlocksForDay)
                    .hasLateCheckIn(hasLate)
                    .hasEarlyCheckOut(hasEarly)
                    .workedMinutes(workedMinutesForDay)
                    .scheduledMinutes(scheduledMinutesForDay)
                    .build());

            chartPoints.add(AttendanceChartPointDto.builder()
                    .date(date)
                    .workedMinutes(workedMinutesForDay)
                    .scheduledMinutes(scheduledMinutesForDay)
                    .completedBlocks(completedBlocksForDay)
                    .scheduledBlocks(scheduledBlocksForDay)
                    .late(hasLate)
                    .early(hasEarly)
                    .build());
        }

        double completionRate = totalBlocks == 0 ? 0.0 : (completedBlocks * 100.0 / totalBlocks);

        return AttendanceMonthlyStatsDto.builder()
                .year(year)
                .month(month)
                .startDate(start)
                .endDate(end)
                .scheduledDays(scheduledDays)
                .attendedDays(attendedDays)
                .absentDays(absentDays)
                .lateCheckIns(lateCheckIns)
                .earlyCheckOuts(earlyCheckOuts)
                .onTimeCheckIns(onTimeCheckIns)
                .onTimeCheckOuts(onTimeCheckOuts)
                .totalBlocks(totalBlocks)
                .completedBlocks(completedBlocks)
                .completionRate(Math.round(completionRate * 100.0) / 100.0)
                .totalWorkedMinutes(totalWorkedMinutes)
                .totalScheduledMinutes(totalScheduledMinutes)
                .lateMinutes(lateMinutes)
                .earlyMinutes(earlyMinutes)
                .dailySummaries(dailySummaries)
                .chartPoints(chartPoints)
                .build();
    }

    @Transactional(readOnly = true)
    public AttendanceInsightDto getMonthlyInsights(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        LocalDate today = LocalDate.now();
        List<ShiftSchedule> schedules = shiftScheduleRepository.findByWorkDateBetween(start, end);
        List<AttendanceRecord> records = attendanceRecordRepository.findAll().stream()
                .filter(r -> !r.getWorkDate().isBefore(start) && !r.getWorkDate().isAfter(end))
                .toList();

        Map<Long, UserStats> stats = new HashMap<>();


        Map<LocalDate, List<AttendanceRecord>> recordMap = records.stream().collect(Collectors.groupingBy(AttendanceRecord::getWorkDate));
        Map<LocalDate, ShiftSchedule> scheduleMap = schedules.stream().collect(Collectors.toMap(ShiftSchedule::getWorkDate, s -> s, (a, b) -> a));

        for (ShiftSchedule schedule : schedules) {
            if (schedule.getUser() == null) continue;
            stats.computeIfAbsent(schedule.getUser().getId(), id -> new UserStats(schedule.getUser().getId(), schedule.getUser().getFullName()));
        }

        for (AttendanceRecord record : records) {
            if (record.getUser() == null) continue;
            UserStats s = stats.computeIfAbsent(record.getUser().getId(), id -> new UserStats(record.getUser().getId(), record.getUser().getFullName()));
            if ("LATE".equals(record.getCheckInStatus())) s.lateCount++;
            if ("EARLY".equals(record.getCheckOutStatus())) s.earlyCount++;
            if (record.getCheckInAt() != null) s.attendedDays++;
            if (record.getCheckInStatus() != null && "LATE".equals(record.getCheckInStatus())) {
                s.score -= 5;
            }
            if (record.getCheckOutStatus() != null && "EARLY".equals(record.getCheckOutStatus())) {
                s.score -= 2;
            }
            if (record.getCheckInStatus() != null && "ON_TIME".equals(record.getCheckInStatus())) s.score += 2;
            if (record.getCheckOutStatus() != null && "ON_TIME".equals(record.getCheckOutStatus())) s.score += 2;
        }

        for (Map.Entry<LocalDate, ShiftSchedule> e : scheduleMap.entrySet()) {
            LocalDate date = e.getKey();
            ShiftSchedule schedule = e.getValue();
            if (date.isAfter(today)) continue;
            List<AttendanceRecord> dayRecords = recordMap.getOrDefault(date, List.of());
            if (schedule != null && Set.of("S", "C", "G").contains(normalize(schedule.getShiftType())) && dayRecords.isEmpty()) {
                if (schedule.getUser() != null) {
                    stats.computeIfAbsent(schedule.getUser().getId(), id -> new UserStats(schedule.getUser().getId(), schedule.getUser().getFullName())).absentCount++;
                    stats.get(schedule.getUser().getId()).score -= 10;
                }
            }
        }

        List<AttendanceRankingItemDto> lateRanking = stats.values().stream()
                .sorted(Comparator.comparingInt((UserStats s) -> s.lateCount).reversed().thenComparingInt(s -> s.attendedDays).reversed())
                .limit(10)
                .map(UserStats::toRankingLate)
                .collect(Collectors.toList());

        List<AttendanceRankingItemDto> absentRanking = stats.values().stream()
                .sorted(Comparator.comparingInt((UserStats s) -> s.absentCount).reversed())
                .limit(10)
                .map(UserStats::toRankingAbsent)
                .collect(Collectors.toList());

        List<AttendanceRankingItemDto> earlyRanking = stats.values().stream()
                .sorted(Comparator.comparingInt((UserStats s) -> s.earlyCount).reversed())
                .limit(10)
                .map(UserStats::toRankingEarly)
                .collect(Collectors.toList());

        List<AttendanceDailySummaryDto> topLateDays = recordMap.entrySet().stream()
                .map(e -> buildDaySummary(e.getKey(), e.getValue(), scheduleMap.get(e.getKey())))
                .filter(d -> d.isHasLateCheckIn())
                .sorted(Comparator.comparingLong(AttendanceDailySummaryDto::getWorkedMinutes).reversed())
                .limit(5)
                .collect(Collectors.toList());

        List<AttendanceDailySummaryDto> topEarlyDays = recordMap.entrySet().stream()
                .map(e -> buildDaySummary(e.getKey(), e.getValue(), scheduleMap.get(e.getKey())))
                .filter(AttendanceDailySummaryDto::isHasEarlyCheckOut)
                .sorted(Comparator.comparingLong(AttendanceDailySummaryDto::getWorkedMinutes).reversed())
                .limit(5)
                .collect(Collectors.toList());

        return AttendanceInsightDto.builder()
                .chartPoints(buildChartPoints(start, end, scheduleMap, recordMap, today))
                .lateRanking(lateRanking)
                .absentRanking(absentRanking)
                .earlyRanking(earlyRanking)
                .topLateDays(topLateDays)
                .topEarlyDays(topEarlyDays)
                .build();
    }

    private List<AttendanceChartPointDto> buildChartPoints(LocalDate start, LocalDate end, Map<LocalDate, ShiftSchedule> scheduleMap, Map<LocalDate, List<AttendanceRecord>> recordMap, LocalDate today) {
        List<AttendanceChartPointDto> points = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            ShiftSchedule schedule = scheduleMap.get(date);
            List<AttendanceRecord> dayRecords = recordMap.getOrDefault(date, List.of());
            AttendanceDailySummaryDto summary = buildDaySummary(date, dayRecords, schedule);
            points.add(AttendanceChartPointDto.builder()
                    .date(date)
                    .workedMinutes(summary.getWorkedMinutes())
                    .scheduledMinutes(summary.getScheduledMinutes())
                    .completedBlocks(summary.getCompletedBlocks())
                    .scheduledBlocks(summary.getScheduledBlocks())
                    .late(summary.isHasLateCheckIn())
                    .early(summary.isHasEarlyCheckOut())
                    .build());
        }
        return points;
    }

    private AttendanceDailySummaryDto buildDaySummary(LocalDate date, List<AttendanceRecord> dayRecords, ShiftSchedule schedule) {
        String shiftType = schedule != null ? normalize(schedule.getShiftType()) : "OFF";
        int scheduledBlocks = schedule == null || !Set.of("S", "C", "G").contains(shiftType)
                ? 0 : ("G".equals(shiftType) ? parseSelectedBlocks(schedule.getSelectedBlocks()).size() : 1);
        long workedMinutes = 0;
        long scheduledMinutes = 0;
        int completedBlocks = 0;
        boolean hasLate = false;
        boolean hasEarly = false;
        for (AttendanceRecord record : dayRecords) {
            ShiftConfigDto.ShiftBlock block = resolveBlock(shiftType, record.getBlockNumber());
            if (block != null) scheduledMinutes += Duration.between(block.getStartTime(), block.getEndTime()).toMinutes();
            if (record.getCheckInAt() != null && record.getCheckOutAt() != null) {
                workedMinutes += Duration.between(record.getCheckInAt(), record.getCheckOutAt()).toMinutes();
                completedBlocks++;
            }
            if ("LATE".equals(record.getCheckInStatus())) hasLate = true;
            if ("EARLY".equals(record.getCheckOutStatus())) hasEarly = true;
        }
        return AttendanceDailySummaryDto.builder()
                .date(date)
                .shiftType(shiftType)
                .dayStatus(dayRecords.isEmpty() ? "ABSENT" : "ON_TIME")
                .scheduledBlocks(scheduledBlocks)
                .completedBlocks(completedBlocks)
                .hasLateCheckIn(hasLate)
                .hasEarlyCheckOut(hasEarly)
                .workedMinutes(workedMinutes)
                .scheduledMinutes(scheduledMinutes)
                .build();
    }

    private ShiftConfigDto.ShiftBlock resolveBlock(String shiftType, Integer blockNumber) {
        if (shiftType == null) return null;
        List<ShiftConfigDto.ShiftBlock> blocks = SHIFT_CONFIG.get(normalize(shiftType));
        if (blocks == null) return null;
        return blocks.stream().filter(b -> Objects.equals(b.getBlockNumber(), blockNumber)).findFirst().orElse(null);
    }

    private long computeLateMinutes(ShiftConfigDto.ShiftBlock block, LocalDateTime checkInAt) {
        if (block == null || checkInAt == null) return 0;
        LocalDateTime start = LocalDateTime.of(checkInAt.toLocalDate(), block.getStartTime());
        return Math.max(0, Duration.between(start, checkInAt).toMinutes());
    }

    private long computeEarlyMinutes(ShiftConfigDto.ShiftBlock block, LocalDateTime checkOutAt) {
        if (block == null || checkOutAt == null) return 0;
        LocalDateTime end = LocalDateTime.of(checkOutAt.toLocalDate(), block.getEndTime());
        return Math.max(0, Duration.between(checkOutAt, end).toMinutes());
    }

    private String determineDayStatus(LocalDate date, boolean hasSchedule, String shiftType, List<AttendanceRecord> records, LocalDate today) {
        if (!hasSchedule) return "NO_SCHEDULE";
        if (!Set.of("S", "C", "G", "P", "OFF").contains(shiftType)) return "OFF";
        if (date.isAfter(today)) return "SCHEDULED";
        if (records.isEmpty()) return date.isBefore(today) ? "ABSENT" : "SCHEDULED";
        boolean hasLateOrEarly = records.stream().anyMatch(r -> "LATE".equals(r.getCheckInStatus()) || "EARLY".equals(r.getCheckOutStatus()));
        if (date.isBefore(today)) return hasLateOrEarly ? "LATE" : "ON_TIME";
        return hasLateOrEarly ? "LATE" : "ON_TIME";
    }

    private String normalize(String shiftType) {
        return String.valueOf(shiftType == null ? "" : shiftType).trim().toUpperCase();
    }

    private List<Integer> parseSelectedBlocks(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(Integer::valueOf)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private static class UserStats {
        Long userId;
        String fullName;
        int lateCount;
        int absentCount;
        int earlyCount;
        int attendedDays;
        int score;

        UserStats(Long userId, String fullName) { this.userId = userId; this.fullName = fullName; }
        AttendanceRankingItemDto toRankingLate() { return AttendanceRankingItemDto.builder().userId(userId).userFullName(fullName).lateCount(lateCount).absentCount(absentCount).earlyCount(earlyCount).attendedDays(attendedDays).score(score).build(); }
        AttendanceRankingItemDto toRankingAbsent() { return AttendanceRankingItemDto.builder().userId(userId).userFullName(fullName).lateCount(lateCount).absentCount(absentCount).earlyCount(earlyCount).attendedDays(attendedDays).score(score).build(); }
        AttendanceRankingItemDto toRankingEarly() { return AttendanceRankingItemDto.builder().userId(userId).userFullName(fullName).lateCount(lateCount).absentCount(absentCount).earlyCount(earlyCount).attendedDays(attendedDays).score(score).build(); }
    }


}
