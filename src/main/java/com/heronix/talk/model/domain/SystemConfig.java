package com.heronix.talk.model.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * System configuration entity for storing key-value settings.
 */
@Entity
@Table(name = "system_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", unique = true, nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", length = 2000)
    private String configValue;

    @Column(name = "config_type", length = 50)
    private String configType; // STRING, INTEGER, BOOLEAN, JSON

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String category; // NETWORK, SECURITY, SYSTEM, UI, SYNC

    @Column(name = "is_sensitive")
    @Builder.Default
    private boolean sensitive = false;

    @Column(name = "is_readonly")
    @Builder.Default
    private boolean readonly = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Convenience methods for type conversion
    public Integer getAsInteger() {
        return configValue != null ? Integer.parseInt(configValue) : null;
    }

    public Boolean getAsBoolean() {
        return configValue != null ? Boolean.parseBoolean(configValue) : null;
    }

    public Long getAsLong() {
        return configValue != null ? Long.parseLong(configValue) : null;
    }
}
