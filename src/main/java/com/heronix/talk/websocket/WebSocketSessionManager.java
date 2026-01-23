package com.heronix.talk.websocket;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages WebSocket sessions with connection limits, health monitoring,
 * and backpressure handling for 200+ concurrent users.
 */
@Component
@Slf4j
public class WebSocketSessionManager {

    @Value("${heronix.websocket.max-sessions:500}")
    private int maxSessions;

    @Value("${heronix.websocket.session-idle-timeout-ms:300000}")
    private long sessionIdleTimeoutMs;

    @Value("${heronix.websocket.send-timeout-ms:10000}")
    private long sendTimeoutMs;

    // Session tracking
    private final Map<String, SessionInfo> sessionInfoMap = new ConcurrentHashMap<>();

    // Metrics
    @Getter
    private final AtomicInteger currentConnections = new AtomicInteger(0);
    @Getter
    private final AtomicInteger peakConnections = new AtomicInteger(0);
    @Getter
    private final AtomicLong totalConnections = new AtomicLong(0);
    @Getter
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    @Getter
    private final AtomicLong totalMessagesSent = new AtomicLong(0);
    @Getter
    private final AtomicLong droppedMessages = new AtomicLong(0);
    @Getter
    private final AtomicLong connectionRejections = new AtomicLong(0);

    /**
     * Check if a new connection can be accepted.
     */
    public boolean canAcceptConnection() {
        int current = currentConnections.get();
        if (current >= maxSessions) {
            connectionRejections.incrementAndGet();
            log.warn("Connection rejected: max sessions reached ({}/{})", current, maxSessions);
            return false;
        }
        return true;
    }

    /**
     * Register a new session.
     */
    public void registerSession(WebSocketSession session, Long userId) {
        String sessionId = session.getId();
        SessionInfo info = new SessionInfo(sessionId, userId, Instant.now());
        sessionInfoMap.put(sessionId, info);

        int current = currentConnections.incrementAndGet();
        totalConnections.incrementAndGet();

        // Track peak connections
        peakConnections.updateAndGet(peak -> Math.max(peak, current));

        log.debug("Session registered: {} for user {} (total: {})", sessionId, userId, current);
    }

    /**
     * Unregister a session.
     */
    public void unregisterSession(String sessionId) {
        SessionInfo info = sessionInfoMap.remove(sessionId);
        if (info != null) {
            currentConnections.decrementAndGet();
            log.debug("Session unregistered: {} (total: {})", sessionId, currentConnections.get());
        }
    }

    /**
     * Record activity for a session (for idle timeout tracking).
     */
    public void recordActivity(String sessionId) {
        SessionInfo info = sessionInfoMap.get(sessionId);
        if (info != null) {
            info.lastActivity = Instant.now();
            totalMessagesReceived.incrementAndGet();
        }
    }

    /**
     * Record a message sent to a session.
     */
    public void recordMessageSent(String sessionId) {
        SessionInfo info = sessionInfoMap.get(sessionId);
        if (info != null) {
            info.messagesSent.incrementAndGet();
            totalMessagesSent.incrementAndGet();
        }
    }

    /**
     * Record a dropped message (backpressure).
     */
    public void recordDroppedMessage(String sessionId) {
        droppedMessages.incrementAndGet();
        SessionInfo info = sessionInfoMap.get(sessionId);
        if (info != null) {
            info.droppedMessages.incrementAndGet();
        }
    }

    /**
     * Check if a session is healthy (not idle too long, not backpressured).
     */
    public boolean isSessionHealthy(String sessionId) {
        SessionInfo info = sessionInfoMap.get(sessionId);
        if (info == null) {
            return false;
        }

        // Check idle timeout
        long idleMs = Instant.now().toEpochMilli() - info.lastActivity.toEpochMilli();
        if (idleMs > sessionIdleTimeoutMs) {
            log.debug("Session {} is idle for {}ms (threshold: {}ms)", sessionId, idleMs, sessionIdleTimeoutMs);
            return false;
        }

        // Check if too many dropped messages (backpressure indicator)
        if (info.droppedMessages.get() > 100) {
            log.warn("Session {} has too many dropped messages: {}", sessionId, info.droppedMessages.get());
            return false;
        }

        return true;
    }

    /**
     * Get session info for a specific session.
     */
    public SessionInfo getSessionInfo(String sessionId) {
        return sessionInfoMap.get(sessionId);
    }

    /**
     * Cleanup idle sessions periodically.
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanupIdleSessions() {
        long now = Instant.now().toEpochMilli();
        int cleaned = 0;

        for (Map.Entry<String, SessionInfo> entry : sessionInfoMap.entrySet()) {
            SessionInfo info = entry.getValue();
            long idleMs = now - info.lastActivity.toEpochMilli();

            if (idleMs > sessionIdleTimeoutMs) {
                log.info("Cleaning up idle session: {} (idle for {}s)", entry.getKey(), idleMs / 1000);
                sessionInfoMap.remove(entry.getKey());
                currentConnections.decrementAndGet();
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.info("Cleaned up {} idle sessions, active: {}", cleaned, currentConnections.get());
        }
    }

    /**
     * Log connection statistics periodically.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void logStatistics() {
        log.info("WebSocket stats: current={}, peak={}, total={}, msgIn={}, msgOut={}, dropped={}, rejected={}",
                currentConnections.get(),
                peakConnections.get(),
                totalConnections.get(),
                totalMessagesReceived.get(),
                totalMessagesSent.get(),
                droppedMessages.get(),
                connectionRejections.get());
    }

    /**
     * Get current statistics as a map.
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
                "currentConnections", currentConnections.get(),
                "peakConnections", peakConnections.get(),
                "totalConnections", totalConnections.get(),
                "totalMessagesReceived", totalMessagesReceived.get(),
                "totalMessagesSent", totalMessagesSent.get(),
                "droppedMessages", droppedMessages.get(),
                "connectionRejections", connectionRejections.get(),
                "maxSessions", maxSessions
        );
    }

    /**
     * Reset peak statistics (for monitoring).
     */
    public void resetPeakStatistics() {
        peakConnections.set(currentConnections.get());
        droppedMessages.set(0);
        connectionRejections.set(0);
        log.info("Peak statistics reset");
    }

    @PreDestroy
    public void shutdown() {
        log.info("WebSocket session manager shutting down with {} active sessions", currentConnections.get());
        sessionInfoMap.clear();
    }

    /**
     * Session information holder.
     */
    @Getter
    public static class SessionInfo {
        private final String sessionId;
        private final Long userId;
        private final Instant connectedAt;
        private volatile Instant lastActivity;
        private final AtomicLong messagesSent = new AtomicLong(0);
        private final AtomicLong droppedMessages = new AtomicLong(0);

        public SessionInfo(String sessionId, Long userId, Instant connectedAt) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.connectedAt = connectedAt;
            this.lastActivity = connectedAt;
        }
    }
}
