package com.heronix.talk.model.dto;

import lombok.*;

/**
 * Request DTO for updating security policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSecurityPolicyRequest {

    // Password Policy
    private Integer minPasswordLength;
    private Integer maxPasswordLength;
    private Boolean requireUppercase;
    private Boolean requireLowercase;
    private Boolean requireNumbers;
    private Boolean requireSpecialChars;
    private String specialCharsAllowed;
    private Integer passwordExpiryDays;
    private Integer passwordHistoryCount;
    private Boolean preventCommonPasswords;

    // Login Security
    private Integer maxLoginAttempts;
    private Integer lockoutDurationMinutes;
    private Integer sessionTimeoutMinutes;
    private Integer concurrentSessionsAllowed;
    private Boolean requireMfa;

    // IP Security
    private Boolean ipWhitelistEnabled;
    private String ipWhitelist;
    private String ipBlacklist;

    // Audit Settings
    private Boolean auditLoginEvents;
    private Boolean auditAdminActions;
    private Boolean auditMessageEvents;
    private Integer auditRetentionDays;

    // Rate Limiting
    private Boolean rateLimitEnabled;
    private Integer rateLimitRequestsPerMinute;
    private Integer rateLimitMessagesPerMinute;
}
