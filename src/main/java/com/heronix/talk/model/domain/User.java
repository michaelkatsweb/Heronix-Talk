package com.heronix.talk.model.domain;

import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.model.enums.UserRole;
import com.heronix.talk.model.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * User entity representing teachers and staff in the messaging system.
 * Can be synced from EduScheduler-Pro or Heronix-Teacher.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_employee_id", columnList = "employeeId", unique = true),
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_username", columnList = "username", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(unique = true, nullable = false)
    private String username;

    @NotBlank
    @Size(max = 50)
    @Column(unique = true)
    private String employeeId;

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @Email
    @Size(max = 150)
    @Column(unique = true)
    private String email;

    @Size(max = 255)
    private String passwordHash;

    @Size(max = 100)
    private String department;

    @Size(max = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.TEACHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.OFFLINE;

    @Size(max = 255)
    private String statusMessage;

    @Size(max = 500)
    private String avatarPath;

    private LocalDateTime lastSeen;

    private LocalDateTime lastActivity;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private boolean locked = false;

    @Builder.Default
    private int failedLoginAttempts = 0;

    @Builder.Default
    private boolean passwordChangeRequired = false;

    @Size(max = 50)
    private String roleName;

    @Builder.Default
    private boolean notificationsEnabled = true;

    @Builder.Default
    private boolean soundEnabled = true;

    // Sync fields (following Heronix pattern)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.PENDING;

    private LocalDateTime lastSyncTime;

    @Size(max = 100)
    private String syncSource;

    // Audit fields
    @Column(updatable = false)
    private LocalDateTime createdDate;

    private LocalDateTime modifiedDate;

    // Relationships
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_channels",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "channel_id")
    )
    @Builder.Default
    private Set<Channel> channels = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        modifiedDate = LocalDateTime.now();
        if (syncStatus == null) {
            syncStatus = SyncStatus.PENDING;
        }
        if (status == null) {
            status = UserStatus.OFFLINE;
        }
        if (role == null) {
            role = UserRole.TEACHER;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedDate = LocalDateTime.now();
        if (syncStatus == SyncStatus.SYNCED) {
            syncStatus = SyncStatus.PENDING;
        }
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getDisplayName() {
        if (statusMessage != null && !statusMessage.isEmpty()) {
            return getFullName() + " - " + statusMessage;
        }
        return getFullName();
    }

    public boolean isOnline() {
        return status != UserStatus.OFFLINE;
    }

    public boolean needsSync() {
        return syncStatus == SyncStatus.PENDING || syncStatus == SyncStatus.LOCAL_ONLY;
    }

    public String getRole() {
        if (roleName != null) {
            return roleName;
        }
        return role != null ? role.name() : "USER";
    }

    public void setRole(String roleName) {
        this.roleName = roleName;
    }
}
