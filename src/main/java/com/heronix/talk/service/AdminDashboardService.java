package com.heronix.talk.service;

import com.heronix.talk.model.dto.AdminDashboardDTO;
import com.heronix.talk.model.dto.AuditLogDTO;
import com.heronix.talk.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service for admin dashboard statistics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;
    private final AuditService auditService;
    private final SessionRepository sessionRepository;

    private static final LocalDateTime serverStartTime = LocalDateTime.now();

    public AdminDashboardDTO getDashboardData() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.truncatedTo(ChronoUnit.DAYS);
        LocalDateTime weekStart = now.minusDays(7);

        // User statistics
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByActiveTrue();
        long lockedUsers = userRepository.countByLockedTrue();
        long onlineUsers = sessionRepository.countActiveSessions();

        // Channel statistics
        long totalChannels = channelRepository.count();
        long activeChannels = channelRepository.countByArchivedFalse();

        // Message statistics
        long totalMessages = messageRepository.count();

        // Security statistics
        long failedLoginAttemptsToday = auditService.getFailedLoginCount(todayStart);
        long lockedAccountsToday = auditService.getLockedAccountCount(todayStart);

        // Recent activity
        List<AuditLogDTO> recentAuditLogs = auditService.getRecentAuditLogs(10);

        // Calculate uptime
        long uptimeSeconds = ChronoUnit.SECONDS.between(serverStartTime, now);

        // Storage (approximate)
        long databaseSize = estimateDatabaseSize();

        return AdminDashboardDTO.builder()
                .systemStatus("ONLINE")
                .serverStartTime(serverStartTime)
                .uptimeSeconds(uptimeSeconds)
                .version("1.0.0")
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .lockedUsers(lockedUsers)
                .onlineUsers(onlineUsers)
                .totalChannels(totalChannels)
                .activeChannels(activeChannels)
                .totalMessages(totalMessages)
                .failedLoginAttemptsToday(failedLoginAttemptsToday)
                .lockedAccountsToday(lockedAccountsToday)
                .recentAuditLogs(recentAuditLogs)
                .databaseSizeBytes(databaseSize)
                .build();
    }

    public long getOnlineUserCount() {
        return sessionRepository.countActiveSessions();
    }

    public String getSystemStatus() {
        // Check various system health indicators
        try {
            // Check database connectivity
            userRepository.count();

            // Check memory usage
            Runtime runtime = Runtime.getRuntime();
            double usedMemoryPercent = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() * 100;

            if (usedMemoryPercent > 90) {
                return "DEGRADED";
            }

            return "ONLINE";
        } catch (Exception e) {
            log.error("System health check failed", e);
            return "DEGRADED";
        }
    }

    public SystemHealthDTO getSystemHealth() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        int availableProcessors = runtime.availableProcessors();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        return new SystemHealthDTO(
                getSystemStatus(),
                usedMemory,
                maxMemory,
                (double) usedMemory / maxMemory * 100,
                availableProcessors,
                uptimeMs / 1000,
                Thread.activeCount()
        );
    }

    private long estimateDatabaseSize() {
        try {
            File dataDir = new File("./data");
            if (dataDir.exists() && dataDir.isDirectory()) {
                return getDirSize(dataDir);
            }
        } catch (Exception e) {
            log.debug("Could not estimate database size", e);
        }
        return 0;
    }

    private long getDirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else {
                    size += getDirSize(file);
                }
            }
        }
        return size;
    }

    public record SystemHealthDTO(
            String status,
            long usedMemoryBytes,
            long maxMemoryBytes,
            double memoryUsagePercent,
            int availableProcessors,
            long uptimeSeconds,
            int activeThreads
    ) {}
}
