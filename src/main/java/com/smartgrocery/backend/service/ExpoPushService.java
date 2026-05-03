package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpoPushService {

    private static final URI EXPO_PUSH_URI = URI.create("https://exp.host/--/api/v2/push/send");

    private final ObjectMapper objectMapper;

    public void sendMulticast(List<String> expoPushTokens, String title, String body, Map<String, String> data) {
        if (expoPushTokens == null || expoPushTokens.isEmpty()) return;

        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            for (String token : expoPushTokens) {
                if (token == null || token.isBlank()) continue;
                messages.add(Map.of(
                        "to", token,
                        "title", title,
                        "body", body,
                        "data", data != null ? data : Map.of()
                ));
            }
            if (messages.isEmpty()) return;

            String json = objectMapper.writeValueAsString(messages);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(EXPO_PUSH_URI)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Expo push multicast sent: {} tokens", expoPushTokens.size());
                return;
            }
            log.warn("Expo push failed: status={}, body={}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.warn("Expo push error: {}", e.getMessage());
        }
    }
}

