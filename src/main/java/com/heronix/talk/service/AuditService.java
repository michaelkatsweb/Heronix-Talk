package com.heronix.talk.service;

import com.heronix.talk.model.domain.AuditLog;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.AuditLogDTO;
import com.heronix.talk.model.enums.AuditAction;
import com.heronix.talk.model.enums.AuditCategory;
import com.heronix.talk.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for audit logging operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an audit event asynchronously.
     */
    @Async
    @Transactional
    public void logAsync(AuditCategory category, AuditAction action, User user, String description) {
        log(category, action, user, null, null, null, description, true, null);
    }

    /**
     * Log an audit event.
     */
    @Transactional
    public AuditLog log(AuditCategory category, AuditAction action, User user, String description) {
        return log(category, action, user, null, null, null, description, true, null);
    }

    /**
     * Log an audit event with target information.
     */
    @Transactional
    public AuditLog log(AuditCategory category, AuditAction action, User user,
                        String targetType, Long targetId, String targetName, String description) {
        return log(category, action, user, targetType, targetId, targetName, description, true, null);
    }

    /**
     * Log an audit event with full details.
     */
    @Transactional
    public AuditLog log(AuditCategory category, AuditAction action, User user,
                        String targetType, Long targetId, String targetName,
                        String description, boolean success, String errorMessage) {

        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .category(category)
                .action(action)
                .description(description)
                .success(success)
                .errorMessage(errorMessage);

        if (user != null) {
            builder.userId(user.getId())
                   .username(user.getUsername());
        }

        if (targetType != null) {
            builder.targetType(targetType)
                   .targetId(targetId)
                   .targetName(targetName);
        }

        // Try to get request context
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                builder.ipAddress(getClientIp(request))
                       .userAgent(request.getHeader("User-Agent"))
                       .requestMethod(request.getMethod())
                       .requestPath(request.getRequestURI());
            }
        } catch (Exception e) {
            // Not in request context, skip request info
        }

        AuditLog auditLog = builder.build();
        AuditLog saved = auditLogRepository.save(auditLog);

        log.debug("Audit logged: {} - {} by {}", category, action, user != null ? user.getUsername() : "system");
        return saved;
    }

    /**
     * Log a configuration change with old and new values.
     */
    @Transactional
    public AuditLog logConfigChange(AuditCategory category, AuditAction action, User user,
                                    String configKey, String oldValue, String newValue) {
        AuditLog auditLog = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .category(category)
                .action(action)
                .userId(user != null ? user.getId() : null)
                .username(user != null ? user.getUsername() : "system")
                .targetType("CONFIG")
                .targetName(configKey)
                .oldValue(oldValue)
                .newValue(newValue)
                .description("Configuration changed: " + configKey)
                .success(true)
                .build();

        return auditLogRepository.save(auditLog);
    }

    /**
     * Log a failed action.
     */
    @Transactional
    public AuditLog logFailure(AuditCategory category, AuditAction action, User user,
                               String description, String errorMessage) {
        return log(category, action, user, null, null, null, description, false, errorMessage);
    }

    /**
     * Log a login attempt.
     */
    @Transactional
    public void logLoginAttempt(String username, boolean success, String ipAddress, String userAgent) {
        AuditLog auditLog = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .category(AuditCategory.AUTHENTICATION)
                .action(success ? AuditAction.LOGIN_SUCCESS : AuditAction.LOGIN_FAILED)
                .username(username)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .description(success ? "User logged in successfully" : "Failed login attempt")
                .success(success)
                .build();

        auditLogRepository.save(auditLog);
    }

    // Query methods

    public Page<AuditLogDTO> getAuditLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.findByOrderByTimestampDesc(pageable)
                .map(AuditLogDTO::fromEntity);
    }

    public Page<AuditLogDTO> getAuditLogsByCategory(AuditCategory category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.findByCategoryOrderByTimestampDesc(category, pageable)
                .map(AuditLogDTO::fromEntity);
    }

    public Page<AuditLogDTO> getAuditLogsByUser(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable)
                .map(AuditLogDTO::fromEntity);
    }

    public Page<AuditLogDTO> searchAuditLogs(String term, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.search(term, pageable)
                .map(AuditLogDTO::fromEntity);
    }

    public List<AuditLogDTO> getRecentAuditLogs(int count) {
        Pageable pageable = PageRequest.of(0, count);
        return auditLogRepository.findRecent(pageable).stream()
                .map(AuditLogDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AuditLogDTO> getSecurityEvents(LocalDateTime since) {
        return auditLogRepository.findSecurityEventsSince(since).stream()
                .map(AuditLogDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public long getFailedLoginCount(LocalDateTime since) {
        return auditLogRepository.countFailedLoginsSince(since);
    }

    public long getLockedAccountCount(LocalDateTime since) {
        return auditLogRepository.countLockedAccountsSince(since);
    }

    public Map<AuditAction, Long> getActionStatistics(LocalDateTime since) {
        return auditLogRepository.countByActionSince(since).stream()
                .collect(Collectors.toMap(
                        arr -> (AuditAction) arr[0],
                        arr -> (Long) arr[1]
                ));
    }

    public Map<AuditCategory, Long> getCategoryStatistics(LocalDateTime since) {
        return auditLogRepository.countByCategorySince(since).stream()
                .collect(Collectors.toMap(
                        arr -> (AuditCategory) arr[0],
                        arr -> (Long) arr[1]
                ));
    }

    /**
     * Clean up old audit logs based on retention policy.
     */
    @Transactional
    public int cleanupOldLogs(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = auditLogRepository.deleteOlderThan(cutoff);
        log.info("Deleted {} audit logs older than {} days", deleted, retentionDays);
        return deleted;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
