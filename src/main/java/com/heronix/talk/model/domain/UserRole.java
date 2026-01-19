package com.heronix.talk.model.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * User role entity for role-based access control.
 */
@Entity
@Table(name = "user_roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String name;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(length = 500)
    private String description;

    // Permission flags - System Administration
    @Column(name = "can_manage_users")
    @Builder.Default
    private boolean canManageUsers = false;

    @Column(name = "can_manage_roles")
    @Builder.Default
    private boolean canManageRoles = false;

    @Column(name = "can_manage_channels")
    @Builder.Default
    private boolean canManageChannels = false;

    @Column(name = "can_manage_system_config")
    @Builder.Default
    private boolean canManageSystemConfig = false;

    @Column(name = "can_manage_security_policy")
    @Builder.Default
    private boolean canManageSecurityPolicy = false;

    @Column(name = "can_manage_network_config")
    @Builder.Default
    private boolean canManageNetworkConfig = false;

    @Column(name = "can_view_audit_logs")
    @Builder.Default
    private boolean canViewAuditLogs = false;

    @Column(name = "can_export_data")
    @Builder.Default
    private boolean canExportData = false;

    @Column(name = "can_import_data")
    @Builder.Default
    private boolean canImportData = false;

    // Permission flags - User Management
    @Column(name = "can_create_users")
    @Builder.Default
    private boolean canCreateUsers = false;

    @Column(name = "can_delete_users")
    @Builder.Default
    private boolean canDeleteUsers = false;

    @Column(name = "can_reset_passwords")
    @Builder.Default
    private boolean canResetPasswords = false;

    @Column(name = "can_lock_users")
    @Builder.Default
    private boolean canLockUsers = false;

    @Column(name = "can_unlock_users")
    @Builder.Default
    private boolean canUnlockUsers = false;

    // Permission flags - Channel Management
    @Column(name = "can_create_channels")
    @Builder.Default
    private boolean canCreateChannels = true;

    @Column(name = "can_delete_channels")
    @Builder.Default
    private boolean canDeleteChannels = false;

    @Column(name = "can_manage_channel_members")
    @Builder.Default
    private boolean canManageChannelMembers = false;

    // Permission flags - Messaging
    @Column(name = "can_send_announcements")
    @Builder.Default
    private boolean canSendAnnouncements = false;

    @Column(name = "can_pin_messages")
    @Builder.Default
    private boolean canPinMessages = false;

    @Column(name = "can_delete_any_message")
    @Builder.Default
    private boolean canDeleteAnyMessage = false;

    @Column(name = "can_edit_any_message")
    @Builder.Default
    private boolean canEditAnyMessage = false;

    // Permission flags - Emergency
    @Column(name = "can_send_emergency_alerts")
    @Builder.Default
    private boolean canSendEmergencyAlerts = false;

    @Column(name = "can_initiate_lockdown")
    @Builder.Default
    private boolean canInitiateLockdown = false;

    // Role hierarchy
    @Column(name = "priority")
    @Builder.Default
    private int priority = 0; // Higher = more permissions

    @Column(name = "is_system_role")
    @Builder.Default
    private boolean systemRole = false; // Cannot be deleted

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

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

    // Convenience methods
    public boolean hasAdminPrivileges() {
        return canManageUsers || canManageRoles || canManageSystemConfig ||
               canManageSecurityPolicy || canManageNetworkConfig;
    }

    public boolean hasModeratorPrivileges() {
        return canDeleteAnyMessage || canEditAnyMessage || canPinMessages ||
               canManageChannelMembers;
    }
}
