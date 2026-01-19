package com.heronix.talk.model.dto;

import com.heronix.talk.model.domain.SecurityPolicy;
import lombok.*;
import java.time.LocalDateTime;

/**
 * DTO for security policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityPolicyDTO {

    private Long id;
    private String name;

    // Password Policy
    private int minPasswordLength;
    private int maxPasswordLength;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireNumbers;
    private boolean requireSpecialChars;
    private String specialCharsAllowed;
    private int passwordExpiryDays;
    private int passwordHistoryCount;
    private boolean preventCommonPasswords;

    // Login Security
    private int maxLoginAttempts;
    private int lockoutDurationMinutes;
    private int sessionTimeoutMinutes;
    private int concurrentSessionsAllowed;
    private boolean requireMfa;

    // IP Security
    private boolean ipWhitelistEnabled;
    private String ipWhitelist;
    private String ipBlacklist;

    // Audit Settings
    private boolean auditLoginEvents;
    private boolean auditAdminActions;
    private boolean auditMessageEvents;
    private int auditRetentionDays;

    // Rate Limiting
    private boolean rateLimitEnabled;
    private int rateLimitRequestsPerMinute;
    private int rateLimitMessagesPerMinute;

    // Status
    private boolean active;
    private boolean defaultPolicy;
    private LocalDateTime updatedAt;

    public static SecurityPolicyDTO fromEntity(SecurityPolicy entity) {
        return SecurityPolicyDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .minPasswordLength(entity.getMinPasswordLength())
                .maxPasswordLength(entity.getMaxPasswordLength())
                .requireUppercase(entity.isRequireUppercase())
                .requireLowercase(entity.isRequireLowercase())
                .requireNumbers(entity.isRequireNumbers())
                .requireSpecialChars(entity.isRequireSpecialChars())
                .specialCharsAllowed(entity.getSpecialCharsAllowed())
                .passwordExpiryDays(entity.getPasswordExpiryDays())
                .passwordHistoryCount(entity.getPasswordHistoryCount())
                .preventCommonPasswords(entity.isPreventCommonPasswords())
                .maxLoginAttempts(entity.getMaxLoginAttempts())
                .lockoutDurationMinutes(entity.getLockoutDurationMinutes())
                .sessionTimeoutMinutes(entity.getSessionTimeoutMinutes())
                .concurrentSessionsAllowed(entity.getConcurrentSessionsAllowed())
                .requireMfa(entity.isRequireMfa())
                .ipWhitelistEnabled(entity.isIpWhitelistEnabled())
                .ipWhitelist(entity.getIpWhitelist())
                .ipBlacklist(entity.getIpBlacklist())
                .auditLoginEvents(entity.isAuditLoginEvents())
                .auditAdminActions(entity.isAuditAdminActions())
                .auditMessageEvents(entity.isAuditMessageEvents())
                .auditRetentionDays(entity.getAuditRetentionDays())
                .rateLimitEnabled(entity.isRateLimitEnabled())
                .rateLimitRequestsPerMinute(entity.getRateLimitRequestsPerMinute())
                .rateLimitMessagesPerMinute(entity.getRateLimitMessagesPerMinute())
                .active(entity.isActive())
                .defaultPolicy(entity.isDefaultPolicy())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
