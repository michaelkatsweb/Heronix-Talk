package com.heronix.talk.service;

import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.domain.UserRole;
import com.heronix.talk.model.dto.CreateUserRoleRequest;
import com.heronix.talk.model.dto.UserRoleDTO;
import com.heronix.talk.model.enums.AuditAction;
import com.heronix.talk.model.enums.AuditCategory;
import com.heronix.talk.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for user role management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;

    public List<UserRoleDTO> getAllRoles() {
        return userRoleRepository.findByActiveTrueOrderByPriorityDesc().stream()
                .map(UserRoleDTO::fromEntity)
                .toList();
    }

    public List<UserRoleDTO> getEditableRoles() {
        return userRoleRepository.findEditableRoles().stream()
                .map(UserRoleDTO::fromEntity)
                .toList();
    }

    public Optional<UserRoleDTO> getRoleById(Long id) {
        return userRoleRepository.findById(id)
                .map(UserRoleDTO::fromEntity);
    }

    public Optional<UserRole> findByName(String name) {
        return userRoleRepository.findByName(name);
    }

    /**
     * Create a new role.
     */
    @Transactional
    public UserRoleDTO createRole(CreateUserRoleRequest request, User admin) {
        if (userRoleRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Role with this name already exists");
        }

        UserRole role = UserRole.builder()
                .name(request.getName())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .canManageUsers(request.isCanManageUsers())
                .canManageRoles(request.isCanManageRoles())
                .canManageChannels(request.isCanManageChannels())
                .canManageSystemConfig(request.isCanManageSystemConfig())
                .canManageSecurityPolicy(request.isCanManageSecurityPolicy())
                .canManageNetworkConfig(request.isCanManageNetworkConfig())
                .canViewAuditLogs(request.isCanViewAuditLogs())
                .canExportData(request.isCanExportData())
                .canImportData(request.isCanImportData())
                .canCreateUsers(request.isCanCreateUsers())
                .canDeleteUsers(request.isCanDeleteUsers())
                .canResetPasswords(request.isCanResetPasswords())
                .canLockUsers(request.isCanLockUsers())
                .canUnlockUsers(request.isCanUnlockUsers())
                .canCreateChannels(request.isCanCreateChannels())
                .canDeleteChannels(request.isCanDeleteChannels())
                .canManageChannelMembers(request.isCanManageChannelMembers())
                .canSendAnnouncements(request.isCanSendAnnouncements())
                .canPinMessages(request.isCanPinMessages())
                .canDeleteAnyMessage(request.isCanDeleteAnyMessage())
                .canEditAnyMessage(request.isCanEditAnyMessage())
                .canSendEmergencyAlerts(request.isCanSendEmergencyAlerts())
                .canInitiateLockdown(request.isCanInitiateLockdown())
                .priority(request.getPriority())
                .active(true)
                .systemRole(false)
                .build();

        UserRole saved = userRoleRepository.save(role);

        auditService.log(AuditCategory.ROLE_MANAGEMENT, AuditAction.ROLE_CREATED, admin,
                "ROLE", saved.getId(), saved.getName(),
                "Created new role: " + saved.getName());

        log.info("Role {} created by {}", saved.getName(), admin.getUsername());
        return UserRoleDTO.fromEntity(saved);
    }

    /**
     * Update an existing role.
     */
    @Transactional
    public UserRoleDTO updateRole(Long id, CreateUserRoleRequest request, User admin) {
        UserRole role = userRoleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (role.isSystemRole()) {
            throw new IllegalArgumentException("Cannot modify system roles");
        }

        if (request.getDisplayName() != null) {
            role.setDisplayName(request.getDisplayName());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        // Update permissions
        role.setCanManageUsers(request.isCanManageUsers());
        role.setCanManageRoles(request.isCanManageRoles());
        role.setCanManageChannels(request.isCanManageChannels());
        role.setCanManageSystemConfig(request.isCanManageSystemConfig());
        role.setCanManageSecurityPolicy(request.isCanManageSecurityPolicy());
        role.setCanManageNetworkConfig(request.isCanManageNetworkConfig());
        role.setCanViewAuditLogs(request.isCanViewAuditLogs());
        role.setCanExportData(request.isCanExportData());
        role.setCanImportData(request.isCanImportData());
        role.setCanCreateUsers(request.isCanCreateUsers());
        role.setCanDeleteUsers(request.isCanDeleteUsers());
        role.setCanResetPasswords(request.isCanResetPasswords());
        role.setCanLockUsers(request.isCanLockUsers());
        role.setCanUnlockUsers(request.isCanUnlockUsers());
        role.setCanCreateChannels(request.isCanCreateChannels());
        role.setCanDeleteChannels(request.isCanDeleteChannels());
        role.setCanManageChannelMembers(request.isCanManageChannelMembers());
        role.setCanSendAnnouncements(request.isCanSendAnnouncements());
        role.setCanPinMessages(request.isCanPinMessages());
        role.setCanDeleteAnyMessage(request.isCanDeleteAnyMessage());
        role.setCanEditAnyMessage(request.isCanEditAnyMessage());
        role.setCanSendEmergencyAlerts(request.isCanSendEmergencyAlerts());
        role.setCanInitiateLockdown(request.isCanInitiateLockdown());
        role.setPriority(request.getPriority());

        UserRole saved = userRoleRepository.save(role);

        auditService.log(AuditCategory.ROLE_MANAGEMENT, AuditAction.ROLE_UPDATED, admin,
                "ROLE", saved.getId(), saved.getName(),
                "Updated role: " + saved.getName());

        log.info("Role {} updated by {}", saved.getName(), admin.getUsername());
        return UserRoleDTO.fromEntity(saved);
    }

    /**
     * Delete a role.
     */
    @Transactional
    public void deleteRole(Long id, User admin) {
        UserRole role = userRoleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (role.isSystemRole()) {
            throw new IllegalArgumentException("Cannot delete system roles");
        }

        String roleName = role.getName();
        userRoleRepository.delete(role);

        auditService.log(AuditCategory.ROLE_MANAGEMENT, AuditAction.ROLE_DELETED, admin,
                "ROLE", id, roleName,
                "Deleted role: " + roleName);

        log.info("Role {} deleted by {}", roleName, admin.getUsername());
    }

    /**
     * Check if a user has a specific permission.
     */
    public boolean hasPermission(User user, String permission) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        UserRole role = userRoleRepository.findByName(user.getRole()).orElse(null);
        if (role == null || !role.isActive()) {
            return false;
        }

        return switch (permission) {
            case "MANAGE_USERS" -> role.isCanManageUsers();
            case "MANAGE_ROLES" -> role.isCanManageRoles();
            case "MANAGE_CHANNELS" -> role.isCanManageChannels();
            case "MANAGE_SYSTEM_CONFIG" -> role.isCanManageSystemConfig();
            case "MANAGE_SECURITY_POLICY" -> role.isCanManageSecurityPolicy();
            case "MANAGE_NETWORK_CONFIG" -> role.isCanManageNetworkConfig();
            case "VIEW_AUDIT_LOGS" -> role.isCanViewAuditLogs();
            case "EXPORT_DATA" -> role.isCanExportData();
            case "IMPORT_DATA" -> role.isCanImportData();
            case "CREATE_USERS" -> role.isCanCreateUsers();
            case "DELETE_USERS" -> role.isCanDeleteUsers();
            case "RESET_PASSWORDS" -> role.isCanResetPasswords();
            case "LOCK_USERS" -> role.isCanLockUsers();
            case "UNLOCK_USERS" -> role.isCanUnlockUsers();
            case "CREATE_CHANNELS" -> role.isCanCreateChannels();
            case "DELETE_CHANNELS" -> role.isCanDeleteChannels();
            case "MANAGE_CHANNEL_MEMBERS" -> role.isCanManageChannelMembers();
            case "SEND_ANNOUNCEMENTS" -> role.isCanSendAnnouncements();
            case "PIN_MESSAGES" -> role.isCanPinMessages();
            case "DELETE_ANY_MESSAGE" -> role.isCanDeleteAnyMessage();
            case "EDIT_ANY_MESSAGE" -> role.isCanEditAnyMessage();
            case "SEND_EMERGENCY_ALERTS" -> role.isCanSendEmergencyAlerts();
            case "INITIATE_LOCKDOWN" -> role.isCanInitiateLockdown();
            default -> false;
        };
    }

    /**
     * Check if user has admin privileges.
     */
    public boolean isAdmin(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        UserRole role = userRoleRepository.findByName(user.getRole()).orElse(null);
        return role != null && role.hasAdminPrivileges();
    }
}
