package com.heronix.talk.model.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks active user sessions for the messaging system.
 * Used for presence detection and WebSocket session management.
 */
@Entity
@Table(name = "user_sessions", indexes = {
        @Index(name = "idx_session_user", columnList = "user_id"),
        @Index(name = "idx_session_token", columnList = "sessionToken", unique = true),
        @Index(name = "idx_session_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Size(max = 100)
    @Column(unique = true, nullable = false)
    private String sessionToken;

    @Size(max = 100)
    private String websocketSessionId;

    @Size(max = 50)
    private String clientType;

    @Size(max = 100)
    private String clientVersion;

    @Size(max = 45)
    private String ipAddress;

    @Size(max = 255)
    private String userAgent;

    @Size(max = 100)
    private String deviceName;

    private LocalDateTime connectedAt;

    private LocalDateTime lastActivityAt;

    private LocalDateTime disconnectedAt;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private boolean rememberMe = false;

    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (sessionToken == null) {
            sessionToken = UUID.randomUUID().toString();
        }
        if (connectedAt == null) {
            connectedAt = LocalDateTime.now();
        }
        lastActivityAt = LocalDateTime.now();
    }

    public void updateActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }

    public void disconnect() {
        this.active = false;
        this.disconnectedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean shouldTimeout(int timeoutMinutes) {
        if (lastActivityAt == null) return true;
        return LocalDateTime.now().isAfter(lastActivityAt.plusMinutes(timeoutMinutes));
    }

    public static String generateToken() {
        return UUID.randomUUID().toString();
    }
}
