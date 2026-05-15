package com.smartgrocery.backend.task;

import com.smartgrocery.backend.repository.jpa.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionCleanupTask {

    private final UserSessionRepository userSessionRepository;

    /**
     * Clean up expired and revoked sessions every day at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupSessions() {
        log.info("Starting session cleanup task...");
        int deletedCount = userSessionRepository.deleteRevokedOrExpired(LocalDateTime.now());
        log.info("Session cleanup completed. Deleted {} sessions.", deletedCount);
    }
}
