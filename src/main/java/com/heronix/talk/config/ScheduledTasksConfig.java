package com.heronix.talk.config;

import com.heronix.talk.service.AuthenticationService;
import com.heronix.talk.service.NewsService;
import com.heronix.talk.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for maintenance operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasksConfig {

    private final AuthenticationService authenticationService;
    private final PresenceService presenceService;
    private final NewsService newsService;

    @Value("${heronix.session.inactivity-timeout-minutes:30}")
    private int inactivityTimeoutMinutes;

    @Value("${heronix.session.stale-session-timeout-minutes:60}")
    private int staleSessionTimeoutMinutes;

    /**
     * Check for inactive users and update their status to AWAY.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void checkInactiveUsers() {
        var inactiveUsers = presenceService.checkInactiveUsers(inactivityTimeoutMinutes);
        if (!inactiveUsers.isEmpty()) {
            log.debug("Marked {} users as away due to inactivity", inactiveUsers.size());
        }
    }

    /**
     * Cleanup stale sessions.
     * Runs every 15 minutes.
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    public void cleanupStaleSessions() {
        int cleaned = authenticationService.cleanupStaleSessions(staleSessionTimeoutMinutes);
        if (cleaned > 0) {
            log.info("Cleaned up {} stale sessions", cleaned);
        }
    }

    /**
     * Cleanup expired news items.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupExpiredNews() {
        int deactivated = newsService.cleanupExpiredNews();
        if (deactivated > 0) {
            log.info("Deactivated {} expired news items", deactivated);
        }
    }

    /**
     * Cleanup typing indicators.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedRate = 30000) // 30 seconds
    public void cleanupTypingIndicators() {
        presenceService.cleanupTypingIndicators();
    }
}
