package com.heronix.admin.model;

import lombok.Data;

@Data
public class SecurityPolicyDTO {
    private Long id;
    private int minPasswordLength;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireNumbers;
    private boolean requireSpecialChars;
    private int passwordExpiryDays;
    private int passwordHistoryCount;
    private int maxLoginAttempts;
    private int lockoutDurationMinutes;
    private int sessionTimeoutMinutes;
    private int maxConcurrentSessions;
    private boolean requireTwoFactor;
    private boolean allowRememberMe;
    private int rememberMeDays;
}
