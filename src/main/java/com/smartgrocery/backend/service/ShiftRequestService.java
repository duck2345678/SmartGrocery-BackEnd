package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AdminShiftRequestItemDto;
import com.smartgrocery.backend.dto.AdminShiftRequestStatusRequest;
import com.smartgrocery.backend.dto.ShiftRequestCreateRequest;
import com.smartgrocery.backend.dto.ShiftRequestDto;
import com.smartgrocery.backend.entity.ShiftRequest;
import com.smartgrocery.backend.entity.ShiftSchedule;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.ShiftRequestRepository;
import com.smartgrocery.backend.repository.ShiftScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShiftRequestService {

    private static final Set<String> ALLOWED_SHIFT_TYPES = Set.of("S", "C", "G");
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final Set<String> ALLOWED_G_BLOCK_COMBINATIONS = Set.of("1,3", "1,4", "2,4");

    private final ShiftRequestRepository shiftRequestRepository;
    private final ShiftScheduleRepository shiftScheduleRepository;

    @Value("${app.shift-request.min-days-ahead:1}")
    private int minDaysAhead;

    @Value("${app.shift-request.max-days-ahead:14}")
    private int maxDaysAhead;

    @Transactional
    public ShiftRequestDto createOrUpdateRequest(User user, ShiftRequestCreateRequest request) {
        if (request == null || request.getWorkDate() == null) {
            throw new IllegalArgumentException("Ngày đăng ký không hợp lệ");
        }
        String shiftType = normalizeShiftType(request.getShiftType());
        if (!ALLOWED_SHIFT_TYPES.contains(shiftType)) {
            throw new IllegalArgumentException("Ca đăng ký không hợp lệ");
        }

        String selectedBlocks = normalizeSelectedBlocks(request.getSelectedBlocks());
        if ("G".equals(shiftType)) {
            validateGBlocks(selectedBlocks);
        } else if (selectedBlocks != null) {
            throw new IllegalArgumentException("Chỉ ca G mới được chọn block");
        }

        LocalDate today = LocalDate.now();
        LocalDate minDate = today.plusDays(Math.max(1, minDaysAhead));
        LocalDate maxDate = today.plusDays(Math.max(1, maxDaysAhead));
        if (request.getWorkDate().isBefore(minDate) || request.getWorkDate().isAfter(maxDate)) {
            throw new IllegalArgumentException("Ngày đăng ký không hợp lệ");
        }

        if (shiftScheduleRepository.findByUser_IdAndWorkDate(user.getId(), request.getWorkDate()).isPresent()) {
            throw new IllegalArgumentException("Ngày này đã có lịch làm việc");
        }

        ShiftRequest sr = shiftRequestRepository.findByUser_IdAndWorkDate(user.getId(), request.getWorkDate()).orElse(null);
        if (sr == null) {
            sr = ShiftRequest.builder()
                    .user(user)
                    .workDate(request.getWorkDate())
                    .shiftType(shiftType)
                    .selectedBlocks(selectedBlocks)
                    .status(STATUS_PENDING)
                    .build();
        } else {
            String status = String.valueOf(sr.getStatus() == null ? "" : sr.getStatus()).toUpperCase();
            if (STATUS_APPROVED.equals(status)) {
                throw new IllegalArgumentException("Đơn đã được duyệt");
            }
            sr.setShiftType(shiftType);
            sr.setSelectedBlocks(selectedBlocks);
            sr.setStatus(STATUS_PENDING);
            sr.setAdminNote(null);
        }

        return toDto(shiftRequestRepository.save(sr));
    }

    @Transactional
    public ShiftRequestDto cancelRequest(User user, Long id) {
        ShiftRequest sr = shiftRequestRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đăng ký"));
        if (sr.getUser() == null || !Objects.equals(sr.getUser().getId(), user.getId())) {
            throw new AccessDeniedException("Access denied");
        }
        String status = String.valueOf(sr.getStatus() == null ? "" : sr.getStatus()).toUpperCase();
        if (!STATUS_PENDING.equals(status)) {
            throw new IllegalArgumentException("Chỉ được hủy khi đơn đang chờ duyệt");
        }
        if (sr.getWorkDate() == null || !sr.getWorkDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Chỉ được hủy đơn cho ngày tương lai");
        }
        sr.setStatus(STATUS_CANCELLED);
        return toDto(shiftRequestRepository.save(sr));
    }

    @Transactional(readOnly = true)
    public List<AdminShiftRequestItemDto> adminList(LocalDate from, LocalDate to, String status) {
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusDays(30);
        String st = status != null && !status.isBlank() ? String.valueOf(status).trim().toUpperCase() : null;

        List<ShiftRequest> requests = st == null
                ? shiftRequestRepository.findByWorkDateBetween(start, end)
                : shiftRequestRepository.findByStatusAndWorkDateBetween(st, start, end);

        Map<String, Long> scheduleCountMap = new HashMap<>();
        List<String> shiftTypes = List.of("S", "C", "G");
        for (ShiftScheduleRepository.ShiftTypeCount row : shiftScheduleRepository.countByDateAndShiftTypeRange(start, end, shiftTypes)) {
            String key = row.getWorkDate() + "|" + String.valueOf(row.getShiftType()).toUpperCase();
            scheduleCountMap.put(key, row.getTotal());
        }

        return requests.stream()
                .sorted((a, b) -> {
                    int cmp = a.getWorkDate().compareTo(b.getWorkDate());
                    if (cmp != 0) return cmp;
                    return Long.compare(a.getId(), b.getId());
                })
                .map(r -> {
                    String key = r.getWorkDate() + "|" + String.valueOf(r.getShiftType()).toUpperCase();
                    long scheduledCount = scheduleCountMap.getOrDefault(key, 0L);
                    return AdminShiftRequestItemDto.builder()
                            .id(r.getId())
                            .userId(r.getUser() != null ? r.getUser().getId() : null)
                            .userFullName(r.getUser() != null ? r.getUser().getFullName() : null)
                            .workDate(r.getWorkDate())
                            .shiftType(r.getShiftType())
                            .selectedBlocks(r.getSelectedBlocks())
                            .status(r.getStatus())
                            .adminNote(r.getAdminNote())
                            .scheduledCount(scheduledCount)
                            .scheduledAfterApprove(STATUS_PENDING.equals(String.valueOf(r.getStatus()).toUpperCase()) ? scheduledCount + 1 : scheduledCount)
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public ShiftRequestDto adminUpdateStatus(Long id, AdminShiftRequestStatusRequest request) {
        if (request == null || request.getStatus() == null) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ");
        }

        String newStatus = String.valueOf(request.getStatus()).trim().toUpperCase();
        if (!STATUS_APPROVED.equals(newStatus) && !STATUS_REJECTED.equals(newStatus)) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ");
        }

        ShiftRequest sr = shiftRequestRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đăng ký"));
        String currentStatus = String.valueOf(sr.getStatus() == null ? "" : sr.getStatus()).toUpperCase();
        if (!STATUS_PENDING.equals(currentStatus)) {
            throw new IllegalArgumentException("Chỉ duyệt/từ chối đơn đang chờ duyệt");
        }

        if (STATUS_APPROVED.equals(newStatus)) {
            String shiftType = normalizeShiftType(sr.getShiftType());
            if (!ALLOWED_SHIFT_TYPES.contains(shiftType)) {
                throw new IllegalArgumentException("Ca đăng ký không hợp lệ");
            }
            if ("G".equals(shiftType)) {
                validateGBlocks(sr.getSelectedBlocks());
            }
            if (shiftScheduleRepository.findByUser_IdAndWorkDate(sr.getUser().getId(), sr.getWorkDate()).isPresent()) {
                throw new IllegalArgumentException("Ngày này đã có lịch làm việc");
            }

            ShiftSchedule schedule = ShiftSchedule.builder()
                    .user(sr.getUser())
                    .workDate(sr.getWorkDate())
                    .shiftType(shiftType)
                    .selectedBlocks(sr.getSelectedBlocks())
                    .build();
            shiftScheduleRepository.save(schedule);

            sr.setStatus(STATUS_APPROVED);
            sr.setAdminNote(null);
        } else {
            sr.setStatus(STATUS_REJECTED);
            sr.setAdminNote(request.getAdminNote());
        }

        return toDto(shiftRequestRepository.save(sr));
    }

    private String normalizeShiftType(String v) {
        return String.valueOf(v == null ? "" : v).trim().toUpperCase();
    }

    private String normalizeSelectedBlocks(List<Integer> selectedBlocks) {
        if (selectedBlocks == null || selectedBlocks.isEmpty()) return null;
        return selectedBlocks.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private void validateGBlocks(String selectedBlocks) {
        if (selectedBlocks == null || selectedBlocks.isBlank()) {
            throw new IllegalArgumentException("Ca G phải chọn đúng 2 block");
        }
        List<Integer> blocks = Arrays.stream(selectedBlocks.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toList());
        if (blocks.size() != 2 || blocks.stream().anyMatch(b -> b < 1 || b > 4)) {
            throw new IllegalArgumentException("Ca G phải chọn đúng 2 block hợp lệ");
        }
        String key = blocks.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
        if (!ALLOWED_G_BLOCK_COMBINATIONS.contains(key)) {
            throw new IllegalArgumentException("Ca G không cho phép chọn block liền kề");
        }
    }

    private ShiftRequestDto toDto(ShiftRequest sr) {
        return ShiftRequestDto.builder()
                .id(sr.getId())
                .workDate(sr.getWorkDate())
                .shiftType(sr.getShiftType())
                .selectedBlocks(sr.getSelectedBlocks())
                .status(sr.getStatus())
                .adminNote(sr.getAdminNote())
                .createdAt(sr.getCreatedAt())
                .updatedAt(sr.getUpdatedAt())
                .build();
    }
}

