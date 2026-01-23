package com.heronix.talk.model.domain;

import com.heronix.talk.model.enums.AlertLevel;
import com.heronix.talk.model.enums.AlertType;
import com.heronix.talk.model.enums.SyncStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Emergency alert entity for broadcasting critical notifications to all users.
 * Supports various alert levels and types for campus-wide communication.
 */
@Entity
@Table(name = "emergency_alerts", indexes = {
        @Index(name = "idx_alert_uuid", columnList = "alertUuid", unique = true),
        @Index(name = "idx_alert_level", columnList = "alertLevel"),
        @Index(name = "idx_alert_active", columnList = "active"),
        @Index(name = "idx_alert_issued", columnList = "issuedAt"),
        @Index(name = "idx_alert_active_level", columnList = "active, alertLevel")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 36)
    private String alertUuid;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false)
    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String message;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AlertLevel alertLevel = AlertLevel.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AlertType alertType = AlertType.ANNOUNCEMENT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_id")
    private User issuedBy;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private boolean requiresAcknowledgment = false;

    @Builder.Default
    private boolean playSound = true;

    @Builder.Default
    private boolean campusWide = true;

    // Target audience (optional filtering)
    @Size(max = 500)
    private String targetRoles;  // Comma-separated roles

    @Size(max = 500)
    private String targetDepartments;  // Comma-separated departments

    // Timing
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime cancelledAt;

    // Acknowledgment tracking
    @Builder.Default
    private int acknowledgmentCount = 0;

    // Sync fields
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.PENDING;

    private LocalDateTime lastSyncTime;

    // Audit fields
    @Column(updatable = false)
    private LocalDateTime createdDate;

    private LocalDateTime modifiedDate;

    @PrePersist
    protected void onCreate() {
        if (alertUuid == null) {
            alertUuid = UUID.randomUUID().toString();
        }
        createdDate = LocalDateTime.now();
        modifiedDate = LocalDateTime.now();
        if (issuedAt == null) {
            issuedAt = LocalDateTime.now();
        }
        // Set default expiration based on alert level
        if (expiresAt == null) {
            expiresAt = calculateDefaultExpiration();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedDate = LocalDateTime.now();
    }

    private LocalDateTime calculateDefaultExpiration() {
        return switch (alertLevel) {
            case EMERGENCY -> issuedAt.plusHours(4);
            case URGENT -> issuedAt.plusHours(2);
            case HIGH -> issuedAt.plusHours(8);
            case NORMAL -> issuedAt.plusDays(1);
            case LOW -> issuedAt.plusDays(7);
        };
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isVisible() {
        return active && !isExpired();
    }

    public void cancel() {
        this.active = false;
        this.cancelledAt = LocalDateTime.now();
    }

    public void incrementAcknowledgment() {
        this.acknowledgmentCount++;
    }

    public String getIssuedByName() {
        return issuedBy != null ? issuedBy.getFullName() : "System";
    }
}
