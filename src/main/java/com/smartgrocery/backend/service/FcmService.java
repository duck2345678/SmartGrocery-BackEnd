package com.smartgrocery.backend.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FcmService {

    /**
     * Sends a push notification to a specific token.
     */
    public void sendPushNotification(String token, String title, String body) {
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Sent message to token: {}, response: {}", token, response);
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send Firebase message: {}", e.getMessage());
        }
    }

    /**
     * Sends a push notification to multiple tokens (Multicast).
     */
    public void sendMulticastNotification(List<String> tokens, String title, String body) {
        sendMulticastNotification(tokens, title, body, null);
    }

    public void sendMulticastNotification(List<String> tokens, String title, String body, Map<String, String> data) {
        if (tokens == null || tokens.isEmpty()) return;

        try {
            MulticastMessage.Builder builder = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    ;

            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            MulticastMessage message = builder.build();

            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("Multicast sent: {} success, {} failure", response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send Multicast Firebase message: {}", e.getMessage());
        }
    }
}
