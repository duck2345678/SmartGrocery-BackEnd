package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.Notification;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.entity.UserDevice;
import com.smartgrocery.backend.repository.NotificationRepository;
import com.smartgrocery.backend.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final FcmService fcmService;

    @Transactional
    public void sendNotification(User user, String title, String body, String type) {
        // 1. Save to database
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(body)
                .notificationType(type)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        // 2. Fetch all devices for this user
        List<UserDevice> devices = userDeviceRepository.findAll().stream()
                .filter(d -> d.getUser().getId().equals(user.getId()))
                .collect(Collectors.toList());

        List<String> tokens = devices.stream()
                .map(UserDevice::getFcmToken)
                .collect(Collectors.toList());

        // 3. Send via FCM
        if (!tokens.isEmpty()) {
            fcmService.sendMulticastNotification(tokens, title, body);
        } else {
            log.info("No registered devices found for user: {}. Only DB notification saved.", user.getEmail());
        }
    }

    /**
     * Broadcasts a notification to all users with a specific role.
     * Useful for notifying all Staff about a new order.
     */
    @Transactional
    public void notifyStaff(String title, String body, String type, List<User> staffMembers) {
        for (User staff : staffMembers) {
            sendNotification(staff, title, body, type);
        }
    }
}
