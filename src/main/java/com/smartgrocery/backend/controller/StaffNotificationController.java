package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.NotificationDto;
import com.smartgrocery.backend.entity.Notification;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.NotificationRepository;
import com.smartgrocery.backend.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/staff/notifications")
@Tag(name = "Staff - Notifications", description = "Thông báo dành cho nhân viên")
@RequiredArgsConstructor
public class StaffNotificationController {

    private final NotificationRepository notificationRepository;

    private void assertStaffRole() {
        if (!SecurityUtils.hasAnyRole("STAFF", "ADMIN")) {
            throw new AccessDeniedException("Access denied");
        }
    }

    @Operation(summary = "Danh sách thông báo của tôi")
    @GetMapping
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public ResponseEntity<List<NotificationDto>> my(@AuthenticationPrincipal User user) {
        assertStaffRole();
        return ResponseEntity.ok(notificationRepository.findTop200ByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toDto)
                .toList());
    }

    @Operation(summary = "Đánh dấu đã đọc")
    @PostMapping("/{id}/read")
    @Transactional(value = "transactionManager")
    public ResponseEntity<NotificationDto> markRead(@AuthenticationPrincipal User user, @PathVariable Long id) {
        assertStaffRole();
        Notification n = notificationRepository.findById(id).orElseThrow(() -> new RuntimeException("Notification not found"));
        if (n.getUser() == null || n.getUser().getId() == null || !n.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Forbidden");
        }
        n.setIsRead(true);
        return ResponseEntity.ok(toDto(notificationRepository.save(n)));
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .notificationType(n.getNotificationType())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(Boolean.TRUE.equals(n.getIsRead()))
                .createdAt(n.getCreatedAt())
                .build();
    }
}
