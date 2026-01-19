package com.heronix.talk.model.domain;

import com.heronix.talk.model.enums.AuditAction;
import com.heronix.talk.model.enums.AuditCategory;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Audit log entity for tracking system events and user actions.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_category", columnList = "category"),
    @Index(name = "idx_audit_action", columnList = "action")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "target_type", length = 50)
    private String targetType; // USER, CHANNEL, MESSAGE, SYSTEM, CONFIG

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_name", length = 255)
    private String targetName;

    @Column(length = 2000)
    private String description;

    @Column(name = "old_value", length = 2000)
    private String oldValue;

    @Column(name = "new_value", length = 2000)
    private String newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "is_success")
    @Builder.Default
    private boolean success = true;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "additional_data", length = 4000)
    private String additionalData; // JSON for extra context

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
