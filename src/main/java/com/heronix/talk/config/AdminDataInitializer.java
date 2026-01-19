package com.heronix.talk.config;

import com.heronix.talk.model.domain.NetworkConfig;
import com.heronix.talk.model.domain.SecurityPolicy;
import com.heronix.talk.model.domain.UserRole;
import com.heronix.talk.repository.NetworkConfigRepository;
import com.heronix.talk.repository.SecurityPolicyRepository;
import com.heronix.talk.repository.UserRoleRepository;
import com.heronix.talk.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initializes default admin panel configuration data.
 */
@Component
@Order(2) // Run after DataInitializer
@RequiredArgsConstructor
@Slf4j
public class AdminDataInitializer implements CommandLineRunner {

    private final UserRoleRepository userRoleRepository;
    private final SecurityPolicyRepository securityPolicyRepository;
    private final NetworkConfigRepository networkConfigRepository;
    private final SystemConfigService systemConfigService;

    @Override
    @Transactional
    public void run(String... args) {
        initializeDefaultRoles();
        initializeDefaultSecurityPolicy();
        initializeDefaultNetworkConfig();
        systemConfigService.initializeDefaults();

        log.info("Admin panel configuration initialized");
    }

    private void initializeDefaultRoles() {
        // Super Admin role
        if (!userRoleRepository.existsByName("SUPER_ADMIN")) {
            UserRole superAdmin = UserRole.builder()
                    .name("SUPER_ADMIN")
                    .displayName("Super Administrator")
                    .description("Full system access with all permissions")
                    .canManageUsers(true)
                    .canManageRoles(true)
                    .canManageChannels(true)
                    .canManageSystemConfig(true)
                    .canManageSecurityPolicy(true)
                    .canManageNetworkConfig(true)
                    .canViewAuditLogs(true)
                    .canExportData(true)
                    .canImportData(true)
                    .canCreateUsers(true)
                    .canDeleteUsers(true)
                    .canResetPasswords(true)
                    .canLockUsers(true)
                    .canUnlockUsers(true)
                    .canCreateChannels(true)
                    .canDeleteChannels(true)
                    .canManageChannelMembers(true)
                    .canSendAnnouncements(true)
                    .canPinMessages(true)
                    .canDeleteAnyMessage(true)
                    .canEditAnyMessage(true)
                    .canSendEmergencyAlerts(true)
                    .canInitiateLockdown(true)
                    .priority(1000)
                    .systemRole(true)
                    .active(true)
                    .build();
            userRoleRepository.save(superAdmin);
            log.info("Created SUPER_ADMIN role");
        }

        // Admin role
        if (!userRoleRepository.existsByName("ADMIN")) {
            UserRole admin = UserRole.builder()
                    .name("ADMIN")
                    .displayName("Administrator")
                    .description("Administrative access with most permissions")
                    .canManageUsers(true)
                    .canManageRoles(false)
                    .canManageChannels(true)
                    .canManageSystemConfig(true)
                    .canManageSecurityPolicy(false)
                    .canManageNetworkConfig(false)
                    .canViewAuditLogs(true)
                    .canExportData(true)
                    .canImportData(true)
                    .canCreateUsers(true)
                    .canDeleteUsers(false)
                    .canResetPasswords(true)
                    .canLockUsers(true)
                    .canUnlockUsers(true)
                    .canCreateChannels(true)
                    .canDeleteChannels(true)
                    .canManageChannelMembers(true)
                    .canSendAnnouncements(true)
                    .canPinMessages(true)
                    .canDeleteAnyMessage(true)
                    .canEditAnyMessage(false)
                    .canSendEmergencyAlerts(true)
                    .canInitiateLockdown(true)
                    .priority(900)
                    .systemRole(true)
                    .active(true)
                    .build();
            userRoleRepository.save(admin);
            log.info("Created ADMIN role");
        }

        // Moderator role
        if (!userRoleRepository.existsByName("MODERATOR")) {
            UserRole moderator = UserRole.builder()
                    .name("MODERATOR")
                    .displayName("Moderator")
                    .description("Can moderate channels and messages")
                    .canManageUsers(false)
                    .canManageRoles(false)
                    .canManageChannels(true)
                    .canManageSystemConfig(false)
                    .canManageSecurityPolicy(false)
                    .canManageNetworkConfig(false)
                    .canViewAuditLogs(false)
                    .canExportData(false)
                    .canImportData(false)
                    .canCreateUsers(false)
                    .canDeleteUsers(false)
                    .canResetPasswords(false)
                    .canLockUsers(false)
                    .canUnlockUsers(false)
                    .canCreateChannels(true)
                    .canDeleteChannels(false)
                    .canManageChannelMembers(true)
                    .canSendAnnouncements(true)
                    .canPinMessages(true)
                    .canDeleteAnyMessage(true)
                    .canEditAnyMessage(false)
                    .canSendEmergencyAlerts(false)
                    .canInitiateLockdown(false)
                    .priority(500)
                    .systemRole(true)
                    .active(true)
                    .build();
            userRoleRepository.save(moderator);
            log.info("Created MODERATOR role");
        }

        // Teacher role
        if (!userRoleRepository.existsByName("TEACHER")) {
            UserRole teacher = UserRole.builder()
                    .name("TEACHER")
                    .displayName("Teacher")
                    .description("Standard teacher access")
                    .canManageUsers(false)
                    .canManageRoles(false)
                    .canManageChannels(false)
                    .canManageSystemConfig(false)
                    .canManageSecurityPolicy(false)
                    .canManageNetworkConfig(false)
                    .canViewAuditLogs(false)
                    .canExportData(false)
                    .canImportData(false)
                    .canCreateUsers(false)
                    .canDeleteUsers(false)
                    .canResetPasswords(false)
                    .canLockUsers(false)
                    .canUnlockUsers(false)
                    .canCreateChannels(true)
                    .canDeleteChannels(false)
                    .canManageChannelMembers(false)
                    .canSendAnnouncements(false)
                    .canPinMessages(false)
                    .canDeleteAnyMessage(false)
                    .canEditAnyMessage(false)
                    .canSendEmergencyAlerts(false)
                    .canInitiateLockdown(false)
                    .priority(100)
                    .systemRole(true)
                    .active(true)
                    .build();
            userRoleRepository.save(teacher);
            log.info("Created TEACHER role");
        }

        // Staff role
        if (!userRoleRepository.existsByName("STAFF")) {
            UserRole staff = UserRole.builder()
                    .name("STAFF")
                    .displayName("Staff")
                    .description("Standard staff access")
                    .canManageUsers(false)
                    .canManageRoles(false)
                    .canManageChannels(false)
                    .canManageSystemConfig(false)
                    .canManageSecurityPolicy(false)
                    .canManageNetworkConfig(false)
                    .canViewAuditLogs(false)
                    .canExportData(false)
                    .canImportData(false)
                    .canCreateUsers(false)
                    .canDeleteUsers(false)
                    .canResetPasswords(false)
                    .canLockUsers(false)
                    .canUnlockUsers(false)
                    .canCreateChannels(false)
                    .canDeleteChannels(false)
                    .canManageChannelMembers(false)
                    .canSendAnnouncements(false)
                    .canPinMessages(false)
                    .canDeleteAnyMessage(false)
                    .canEditAnyMessage(false)
                    .canSendEmergencyAlerts(false)
                    .canInitiateLockdown(false)
                    .priority(50)
                    .systemRole(true)
                    .active(true)
                    .build();
            userRoleRepository.save(staff);
            log.info("Created STAFF role");
        }
    }

    private void initializeDefaultSecurityPolicy() {
        if (securityPolicyRepository.findByDefaultPolicyTrue().isEmpty()) {
            SecurityPolicy defaultPolicy = SecurityPolicy.builder()
                    .name("default")
                    .minPasswordLength(8)
                    .maxPasswordLength(128)
                    .requireUppercase(true)
                    .requireLowercase(true)
                    .requireNumbers(true)
                    .requireSpecialChars(true)
                    .specialCharsAllowed("!@#$%^&*()_+-=[]{}|;':\",./<>?")
                    .passwordExpiryDays(90)
                    .passwordHistoryCount(5)
                    .preventCommonPasswords(true)
                    .maxLoginAttempts(5)
                    .lockoutDurationMinutes(30)
                    .sessionTimeoutMinutes(480)
                    .concurrentSessionsAllowed(3)
                    .requireMfa(false)
                    .ipWhitelistEnabled(false)
                    .auditLoginEvents(true)
                    .auditAdminActions(true)
                    .auditMessageEvents(false)
                    .auditRetentionDays(365)
                    .rateLimitEnabled(true)
                    .rateLimitRequestsPerMinute(60)
                    .rateLimitMessagesPerMinute(30)
                    .active(true)
                    .defaultPolicy(true)
                    .build();
            securityPolicyRepository.save(defaultPolicy);
            log.info("Created default security policy");
        }
    }

    private void initializeDefaultNetworkConfig() {
        if (networkConfigRepository.findFirstByActiveTrue().isEmpty()) {
            NetworkConfig defaultConfig = NetworkConfig.builder()
                    .name("default")
                    .serverHost("0.0.0.0")
                    .serverPort(9680)
                    .sslEnabled(false)
                    .proxyEnabled(false)
                    .websocketEnabled(true)
                    .websocketPath("/ws/chat")
                    .websocketHeartbeatInterval(30)
                    .websocketMaxMessageSize(65536)
                    .corsEnabled(true)
                    .corsAllowedOrigins("*")
                    .corsAllowedMethods("GET,POST,PUT,DELETE,OPTIONS")
                    .corsAllowedHeaders("*")
                    .maxConnections(1000)
                    .connectionTimeout(30000)
                    .readTimeout(60000)
                    .writeTimeout(60000)
                    .active(true)
                    .build();
            networkConfigRepository.save(defaultConfig);
            log.info("Created default network configuration");
        }
    }
}
