package com.heronix.talk.model.dto;

import com.heronix.talk.model.domain.UserRole;
import lombok.*;
import java.time.LocalDateTime;

/**
 * DTO for user roles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleDTO {

    private Long id;
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
    private boolean systemRole;
    private boolean active;
    private LocalDateTime updatedAt;

    public static UserRoleDTO fromEntity(UserRole entity) {
        return UserRoleDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .displayName(entity.getDisplayName())
                .description(entity.getDescription())
                .canManageUsers(entity.isCanManageUsers())
                .canManageRoles(entity.isCanManageRoles())
                .canManageChannels(entity.isCanManageChannels())
                .canManageSystemConfig(entity.isCanManageSystemConfig())
                .canManageSecurityPolicy(entity.isCanManageSecurityPolicy())
                .canManageNetworkConfig(entity.isCanManageNetworkConfig())
                .canViewAuditLogs(entity.isCanViewAuditLogs())
                .canExportData(entity.isCanExportData())
                .canImportData(entity.isCanImportData())
                .canCreateUsers(entity.isCanCreateUsers())
                .canDeleteUsers(entity.isCanDeleteUsers())
                .canResetPasswords(entity.isCanResetPasswords())
                .canLockUsers(entity.isCanLockUsers())
                .canUnlockUsers(entity.isCanUnlockUsers())
                .canCreateChannels(entity.isCanCreateChannels())
                .canDeleteChannels(entity.isCanDeleteChannels())
                .canManageChannelMembers(entity.isCanManageChannelMembers())
                .canSendAnnouncements(entity.isCanSendAnnouncements())
                .canPinMessages(entity.isCanPinMessages())
                .canDeleteAnyMessage(entity.isCanDeleteAnyMessage())
                .canEditAnyMessage(entity.isCanEditAnyMessage())
                .canSendEmergencyAlerts(entity.isCanSendEmergencyAlerts())
                .canInitiateLockdown(entity.isCanInitiateLockdown())
                .priority(entity.getPriority())
                .systemRole(entity.isSystemRole())
                .active(entity.isActive())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
