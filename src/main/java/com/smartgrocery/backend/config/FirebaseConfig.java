package com.smartgrocery.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${FIREBASE_CREDENTIALS_BASE64:#{null}}")
    private String firebaseCredentialsBase64;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                if (firebaseCredentialsBase64 == null || firebaseCredentialsBase64.isEmpty() || firebaseCredentialsBase64.equals("your_base64_json_here")) {
                    log.warn("Firebase credentials NOT found in environment! Notification and Auth features might fail.");
                    return;
                }

                byte[] decodedBytes = Base64.getDecoder().decode(firebaseCredentialsBase64);
                try (InputStream serviceAccount = new ByteArrayInputStream(decodedBytes)) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();

                    FirebaseApp.initializeApp(options);
                    log.info("Firebase Application has been initialized successfully!");
                }
            }
        } catch (IOException e) {
            log.error("Error initializing Firebase Admin SDK", e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Base64 string for FIREBASE_CREDENTIALS_BASE64", e);
        }
    }
}
