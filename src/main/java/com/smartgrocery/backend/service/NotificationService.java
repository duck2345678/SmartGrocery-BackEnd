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
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final FcmService fcmService;
    private final ExpoPushService expoPushService;

    @Transactional
    public void sendNotification(User user, String title, String body, String type) {
        sendNotification(user, title, body, type, null);
    }

    @Transactional
    public void sendNotification(User user, String title, String body, String type, Map<String, String> data) {
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
        List<UserDevice> devices = userDeviceRepository.findByUser_Id(user.getId());

        List<String> tokens = devices.stream()
                .map(UserDevice::getFcmToken)
                .collect(Collectors.toList());

        Map<String, String> pushData = data != null ? data : Map.of("type", type);

        List<String> expoTokens = tokens.stream()
                .filter(t -> t != null && t.startsWith("ExponentPushToken"))
                .collect(Collectors.toList());
        List<String> fcmTokens = tokens.stream()
                .filter(t -> t != null && !t.startsWith("ExponentPushToken"))
                .collect(Collectors.toList());

        if (!expoTokens.isEmpty()) {
            expoPushService.sendMulticast(expoTokens, title, body, pushData);
        }
        if (!fcmTokens.isEmpty()) {
            fcmService.sendMulticastNotification(fcmTokens, title, body, pushData);
        }

        if (expoTokens.isEmpty() && fcmTokens.isEmpty()) {
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
            sendNotification(staff, title, body, type, null);
        }
    }

    @Transactional
    public void notifyStaff(String title, String body, String type, Map<String, String> data, List<User> staffMembers) {
        for (User staff : staffMembers) {
            sendNotification(staff, title, body, type, data);
        }
    }
}
