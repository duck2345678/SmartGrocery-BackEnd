package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.ApiResponse;
import com.smartgrocery.backend.entity.Notification;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Quản lý thông báo cho User và Staff")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @Operation(summary = "Lấy danh sách thông báo của người dùng hiện tại")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Notification>>> getNotifications(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        List<Notification> list = notificationRepository.findTop200ByUser_IdOrderByCreatedAtDesc(user.getId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    @PutMapping("/{id}/read")
    @Transactional(value = "transactionManager")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo"));
        
        if (!notification.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build(); // Forbidden
        }
        
        notification.setIsRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Đánh dấu tất cả thông báo là đã đọc")
    @PutMapping("/read-all")
    @Transactional(value = "transactionManager")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        List<Notification> list = notificationRepository.findTop200ByUser_IdOrderByCreatedAtDesc(user.getId());
        for (Notification notification : list) {
            if (Boolean.FALSE.equals(notification.getIsRead()) || notification.getIsRead() == null) {
                notification.setIsRead(true);
                notificationRepository.save(notification);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
