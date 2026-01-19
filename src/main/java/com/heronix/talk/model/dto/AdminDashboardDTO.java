package com.heronix.talk.model.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for admin dashboard statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardDTO {

    // System Status
    private String systemStatus; // ONLINE, MAINTENANCE, DEGRADED
    private LocalDateTime serverStartTime;
    private long uptimeSeconds;
    private String version;

    // User Statistics
    private long totalUsers;
    private long activeUsers;
    private long lockedUsers;
    private long onlineUsers;
    private long newUsersToday;
    private long newUsersThisWeek;

    // Channel Statistics
    private long totalChannels;
    private long activeChannels;
    private long publicChannels;
    private long privateChannels;
    private long directMessageChannels;

    // Message Statistics
    private long totalMessages;
    private long messagesToday;
    private long messagesThisWeek;
    private double averageMessagesPerDay;

    // Security Statistics
    private long failedLoginAttemptsToday;
    private long lockedAccountsToday;
    private long activeSecurityAlerts;

    // Recent Activity
    private List<AuditLogDTO> recentAuditLogs;

    // Connection Statistics
    private int activeWebSocketConnections;
    private int peakConnectionsToday;

    // Storage Statistics
    private long databaseSizeBytes;
    private long attachmentStorageSizeBytes;
    private long logFilesSizeBytes;

    // Performance Metrics
    private double averageResponseTimeMs;
    private long requestsPerMinute;
    private Map<String, Long> requestsByEndpoint;
}
