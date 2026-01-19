package com.heronix.talk.model.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Security policy configuration for the system.
 */
@Entity
@Table(name = "security_policy")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String name;

    // Password Policy
    @Column(name = "min_password_length")
    @Builder.Default
    private int minPasswordLength = 8;

    @Column(name = "max_password_length")
    @Builder.Default
    private int maxPasswordLength = 128;

    @Column(name = "require_uppercase")
    @Builder.Default
    private boolean requireUppercase = true;

    @Column(name = "require_lowercase")
    @Builder.Default
    private boolean requireLowercase = true;

    @Column(name = "require_numbers")
    @Builder.Default
    private boolean requireNumbers = true;

    @Column(name = "require_special_chars")
    @Builder.Default
    private boolean requireSpecialChars = true;

    @Column(name = "special_chars_allowed", length = 100)
    @Builder.Default
    private String specialCharsAllowed = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

    @Column(name = "password_expiry_days")
    @Builder.Default
    private int passwordExpiryDays = 90;

    @Column(name = "password_history_count")
    @Builder.Default
    private int passwordHistoryCount = 5;

    @Column(name = "prevent_common_passwords")
    @Builder.Default
    private boolean preventCommonPasswords = true;

    // Login Security
    @Column(name = "max_login_attempts")
    @Builder.Default
    private int maxLoginAttempts = 5;

    @Column(name = "lockout_duration_minutes")
    @Builder.Default
    private int lockoutDurationMinutes = 30;

    @Column(name = "session_timeout_minutes")
    @Builder.Default
    private int sessionTimeoutMinutes = 480; // 8 hours

    @Column(name = "concurrent_sessions_allowed")
    @Builder.Default
    private int concurrentSessionsAllowed = 3;

    @Column(name = "require_mfa")
    @Builder.Default
    private boolean requireMfa = false;

    // IP Security
    @Column(name = "ip_whitelist_enabled")
    @Builder.Default
    private boolean ipWhitelistEnabled = false;

    @Column(name = "ip_whitelist", length = 2000)
    private String ipWhitelist; // Comma-separated IPs or CIDR ranges

    @Column(name = "ip_blacklist", length = 2000)
    private String ipBlacklist;

    // Audit Settings
    @Column(name = "audit_login_events")
    @Builder.Default
    private boolean auditLoginEvents = true;

    @Column(name = "audit_admin_actions")
    @Builder.Default
    private boolean auditAdminActions = true;

    @Column(name = "audit_message_events")
    @Builder.Default
    private boolean auditMessageEvents = false;

    @Column(name = "audit_retention_days")
    @Builder.Default
    private int auditRetentionDays = 365;

    // Rate Limiting
    @Column(name = "rate_limit_enabled")
    @Builder.Default
    private boolean rateLimitEnabled = true;

    @Column(name = "rate_limit_requests_per_minute")
    @Builder.Default
    private int rateLimitRequestsPerMinute = 60;

    @Column(name = "rate_limit_messages_per_minute")
    @Builder.Default
    private int rateLimitMessagesPerMinute = 30;

    // Status
    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_default")
    @Builder.Default
    private boolean defaultPolicy = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
