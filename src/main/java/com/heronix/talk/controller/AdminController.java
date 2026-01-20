package com.heronix.talk.controller;

import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.*;
import com.heronix.talk.model.enums.AuditAction;
import com.heronix.talk.model.enums.AuditCategory;
import com.heronix.talk.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for admin operations.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminDashboardService dashboardService;
    private final SystemConfigService systemConfigService;
    private final UserRoleService userRoleService;
    private final AuditService auditService;
    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final SisSyncService sisSyncService;
    private final SisDirectDbSyncService sisDirectDbSyncService;
    private final UserImportService userImportService;
    private final ChannelService channelService;

    // ==================== Dashboard ====================

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDTO> getDashboard(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            AdminDashboardDTO dashboard = dashboardService.getDashboardData();
            return ResponseEntity.ok(dashboard);
        });
    }

    @GetMapping("/health")
    public ResponseEntity<AdminDashboardService.SystemHealthDTO> getSystemHealth(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            return ResponseEntity.ok(dashboardService.getSystemHealth());
        });
    }

    // ==================== System Configuration ====================

    @GetMapping("/config")
    public ResponseEntity<List<SystemConfigDTO>> getAllConfigs(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "MANAGE_SYSTEM_CONFIG")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(systemConfigService.getAllConfigs());
        });
    }

    @GetMapping("/config/category/{category}")
    public ResponseEntity<List<SystemConfigDTO>> getConfigsByCategory(
            @PathVariable String category,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "MANAGE_SYSTEM_CONFIG")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(systemConfigService.getConfigsByCategory(category));
        });
    }

    @GetMapping("/config/categories")
    public ResponseEntity<List<String>> getConfigCategories(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            return ResponseEntity.ok(systemConfigService.getAllCategories());
        });
    }

    @PutMapping("/config/{key}")
    public ResponseEntity<SystemConfigDTO> updateConfig(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "MANAGE_SYSTEM_CONFIG")) {
                return ResponseEntity.status(403).build();
            }
            String value = body.get("value");
            SystemConfigDTO config = systemConfigService.setValue(key, value, user);
            return ResponseEntity.ok(config);
        });
    }

    // ==================== User Roles ====================

    @GetMapping("/roles")
    public ResponseEntity<List<UserRoleDTO>> getAllRoles(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            return ResponseEntity.ok(userRoleService.getAllRoles());
        });
    }

    @GetMapping("/roles/{id}")
    public ResponseEntity<UserRoleDTO> getRoleById(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            return userRoleService.getRoleById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        });
    }

    @PostMapping("/roles")
    public ResponseEntity<UserRoleDTO> createRole(
            @RequestBody CreateUserRoleRequest request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "MANAGE_ROLES")) {
                return ResponseEntity.status(403).build();
            }
            UserRoleDTO role = userRoleService.createRole(request, user);
            return ResponseEntity.ok(role);
        });
    }

    @PutMapping("/roles/{id}")
    public ResponseEntity<UserRoleDTO> updateRole(
            @PathVariable Long id,
            @RequestBody CreateUserRoleRequest request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "MANAGE_ROLES")) {
                return ResponseEntity.status(403).build();
            }
            UserRoleDTO role = userRoleService.updateRole(id, request, user);
            return ResponseEntity.ok(role);
        });
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "MANAGE_ROLES")) {
                return ResponseEntity.status(403).build();
            }
            userRoleService.deleteRole(id, user);
            return ResponseEntity.ok().build();
        });
    }

    // ==================== User Management ====================

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "MANAGE_USERS")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(userService.getAllUsers());
        });
    }

    @PostMapping("/users/{userId}/lock")
    public ResponseEntity<Void> lockUser(
            @PathVariable Long userId,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, admin -> {
            if (!userRoleService.hasPermission(admin, "LOCK_USERS")) {
                return ResponseEntity.status(403).build();
            }
            userService.lockUser(userId, admin);
            return ResponseEntity.ok().build();
        });
    }

    @PostMapping("/users/{userId}/unlock")
    public ResponseEntity<Void> unlockUser(
            @PathVariable Long userId,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, admin -> {
            if (!userRoleService.hasPermission(admin, "UNLOCK_USERS")) {
                return ResponseEntity.status(403).build();
            }
            userService.unlockUser(userId, admin);
            return ResponseEntity.ok().build();
        });
    }

    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<Map<String, String>> resetUserPassword(
            @PathVariable Long userId,
            @RequestBody PasswordResetRequest request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, admin -> {
            if (!userRoleService.hasPermission(admin, "RESET_PASSWORDS")) {
                return ResponseEntity.status(403).build();
            }
            String newPassword = userService.resetPassword(userId, request, admin);
            return ResponseEntity.ok(Map.of("temporaryPassword", newPassword));
        });
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, admin -> {
            if (!userRoleService.hasPermission(admin, "MANAGE_USERS")) {
                return ResponseEntity.status(403).build();
            }
            String roleName = body.get("role");
            userService.updateUserRole(userId, roleName, admin);
            return ResponseEntity.ok().build();
        });
    }

    @PostMapping("/users/bulk-action")
    public ResponseEntity<Map<String, Object>> bulkUserAction(
            @RequestBody BulkUserActionRequest request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, admin -> {
            if (!userRoleService.hasPermission(admin, "MANAGE_USERS")) {
                return ResponseEntity.status(403).build();
            }
            int affected = userService.performBulkAction(request, admin);
            return ResponseEntity.ok(Map.of("affectedUsers", affected));
        });
    }

    @PostMapping("/users/sync-channels")
    public ResponseEntity<Map<String, Object>> syncAllUsersToPublicChannels(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, admin -> {
            if (!userRoleService.hasPermission(admin, "MANAGE_USERS")) {
                return ResponseEntity.status(403).build();
            }
            int synced = channelService.syncAllUsersToPublicChannels();
            auditService.logAdminAction(admin, "SYNC_CHANNELS",
                    String.format("Synced %d users to public channels", synced));
            return ResponseEntity.ok(Map.of(
                    "usersProcessed", synced,
                    "timestamp", LocalDateTime.now().toString()
            ));
        });
    }

    // ==================== Audit Logs ====================

    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "VIEW_AUDIT_LOGS")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(auditService.getAuditLogs(page, size));
        });
    }

    @GetMapping("/audit/category/{category}")
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogsByCategory(
            @PathVariable AuditCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "VIEW_AUDIT_LOGS")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(auditService.getAuditLogsByCategory(category, page, size));
        });
    }

    @GetMapping("/audit/user/{userId}")
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "VIEW_AUDIT_LOGS")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(auditService.getAuditLogsByUser(userId, page, size));
        });
    }

    @GetMapping("/audit/search")
    public ResponseEntity<Page<AuditLogDTO>> searchAuditLogs(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "VIEW_AUDIT_LOGS")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(auditService.searchAuditLogs(q, page, size));
        });
    }

    @GetMapping("/audit/security")
    public ResponseEntity<List<AuditLogDTO>> getSecurityEvents(
            @RequestParam(defaultValue = "24") int hours,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "VIEW_AUDIT_LOGS")) {
                return ResponseEntity.status(403).build();
            }
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            return ResponseEntity.ok(auditService.getSecurityEvents(since));
        });
    }

    // ==================== SIS Sync & Import ====================

    @GetMapping("/sis/status")
    public ResponseEntity<SisSyncService.SisSyncStatusDTO> getSisStatus(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "IMPORT_DATA")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(sisSyncService.getStatus());
        });
    }

    @PostMapping("/sis/sync")
    public ResponseEntity<ImportResultDTO> triggerSisSync(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "IMPORT_DATA")) {
                return ResponseEntity.status(403).build();
            }
            ImportResultDTO result = sisSyncService.syncFromApi();
            return ResponseEntity.ok(result);
        });
    }

    @PostMapping("/sis/test-connection")
    public ResponseEntity<Map<String, Object>> testSisConnection(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "IMPORT_DATA")) {
                return ResponseEntity.status(403).build();
            }
            boolean connected = sisSyncService.testConnection();
            return ResponseEntity.ok(Map.of(
                    "connected", connected,
                    "timestamp", LocalDateTime.now().toString()
            ));
        });
    }

    // ==================== SIS Direct Database Sync ====================

    @GetMapping("/sis/db/status")
    public ResponseEntity<SisDirectDbSyncService.DbSyncStatusDTO> getSisDbStatus(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "IMPORT_DATA")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(sisDirectDbSyncService.getStatus());
        });
    }

    @PostMapping("/sis/db/sync")
    public ResponseEntity<ImportResultDTO> triggerSisDbSync(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "IMPORT_DATA")) {
                return ResponseEntity.status(403).build();
            }
            ImportResultDTO result = sisDirectDbSyncService.syncFromDatabase();
            return ResponseEntity.ok(result);
        });
    }

    @PostMapping("/sis/db/test-connection")
    public ResponseEntity<Map<String, Object>> testSisDbConnection(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "IMPORT_DATA")) {
                return ResponseEntity.status(403).build();
            }
            boolean connected = sisDirectDbSyncService.testConnection();
            return ResponseEntity.ok(Map.of(
                    "connected", connected,
                    "timestamp", LocalDateTime.now().toString()
            ));
        });
    }

    // ==================== User Import ====================

    @PostMapping(value = "/users/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importUsersFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "IMPORT_DATA")) {
                return ResponseEntity.status(403).build();
            }
            ImportResultDTO result = userImportService.importFromFile(file);
            return ResponseEntity.ok(result);
        });
    }

    @PostMapping("/users/import/path")
    public ResponseEntity<ImportResultDTO> importUsersFromPath(
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "IMPORT_DATA")) {
                return ResponseEntity.status(403).build();
            }
            String filePath = body.get("path");
            if (filePath == null || filePath.isBlank()) {
                return ResponseEntity.badRequest().body(
                        ImportResultDTO.createError("path", "File path is required"));
            }
            ImportResultDTO result = userImportService.importFromPath(filePath);
            return ResponseEntity.ok(result);
        });
    }

    @GetMapping("/users/import/files")
    public ResponseEntity<List<String>> listImportFiles(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            if (!userRoleService.hasPermission(user, "IMPORT_DATA")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(userImportService.listImportFiles());
        });
    }

    @GetMapping("/users/import/template/csv")
    public ResponseEntity<String> getCsvTemplate(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(userImportService.getCsvTemplate());
        });
    }

    @GetMapping("/users/import/template/json")
    public ResponseEntity<String> getJsonTemplate(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withAdminUser(sessionToken, user -> {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(userImportService.getJsonTemplate());
        });
    }

    // ==================== Helper Methods ====================

    private <T> ResponseEntity<T> withAdminUser(String sessionToken, AdminOperation<T> operation) {
        return authenticationService.getUserFromSession(sessionToken)
                .filter(user -> userRoleService.isAdmin(user) || "ADMIN".equalsIgnoreCase(user.getRoleDisplayName()))
                .map(operation::execute)
                .orElse(ResponseEntity.status(403).build());
    }

    @FunctionalInterface
    private interface AdminOperation<T> {
        ResponseEntity<T> execute(User user);
    }
}
