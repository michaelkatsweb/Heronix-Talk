package com.heronix.talk.model.dto;

import lombok.*;

/**
 * Request DTO for creating/updating user roles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRoleRequest {

    private String name;
    private String displayName;
    private String description;

    // Permission flags - System Administration
    private boolean canManageUsers;
    private boolean canManageRoles;
    private boolean canManageChannels;
    private boolean canManageSystemConfig;
    private boolean canManageSecurityPolicy;
    private boolean canManageNetworkConfig;
    private boolean canViewAuditLogs;
    private boolean canExportData;
    private boolean canImportData;

    // Permission flags - User Management
    private boolean canCreateUsers;
    private boolean canDeleteUsers;
    private boolean canResetPasswords;
    private boolean canLockUsers;
    private boolean canUnlockUsers;

    // Permission flags - Channel Management
    private boolean canCreateChannels;
    private boolean canDeleteChannels;
    private boolean canManageChannelMembers;

    // Permission flags - Messaging
    private boolean canSendAnnouncements;
    private boolean canPinMessages;
    private boolean canDeleteAnyMessage;
    private boolean canEditAnyMessage;

    // Permission flags - Emergency
    private boolean canSendEmergencyAlerts;
    private boolean canInitiateLockdown;

    // Role hierarchy
    private int priority;
}
