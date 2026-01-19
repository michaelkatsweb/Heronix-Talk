package com.heronix.talk.service;

import com.heronix.talk.model.domain.SystemConfig;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.SystemConfigDTO;
import com.heronix.talk.model.enums.AuditAction;
import com.heronix.talk.model.enums.AuditCategory;
import com.heronix.talk.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for system configuration management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final AuditService auditService;

    public List<SystemConfigDTO> getAllConfigs() {
        return systemConfigRepository.findAllOrderedByCategoryAndKey().stream()
                .map(SystemConfigDTO::fromEntity)
                .toList();
    }

    public List<SystemConfigDTO> getConfigsByCategory(String category) {
        return systemConfigRepository.findByCategoryOrderByConfigKeyAsc(category).stream()
                .map(SystemConfigDTO::fromEntity)
                .toList();
    }

    public List<String> getAllCategories() {
        return systemConfigRepository.findAllCategories();
    }

    public Optional<String> getValue(String key) {
        return systemConfigRepository.findValueByKey(key);
    }

    public String getValue(String key, String defaultValue) {
        return systemConfigRepository.findValueByKey(key).orElse(defaultValue);
    }

    public Integer getIntValue(String key, int defaultValue) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getAsInteger)
                .orElse(defaultValue);
    }

    public Boolean getBoolValue(String key, boolean defaultValue) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getAsBoolean)
                .orElse(defaultValue);
    }

    public Map<String, String> getConfigMap(String category) {
        return systemConfigRepository.findByCategoryOrderByConfigKeyAsc(category).stream()
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        c -> c.getConfigValue() != null ? c.getConfigValue() : ""
                ));
    }

    /**
     * Set a configuration value.
     */
    @Transactional
    public SystemConfigDTO setValue(String key, String value, User admin) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Configuration key not found: " + key));

        if (config.isReadonly()) {
            throw new IllegalArgumentException("Configuration is read-only: " + key);
        }

        String oldValue = config.getConfigValue();
        config.setConfigValue(value);
        config.setUpdatedBy(admin != null ? admin.getUsername() : "system");

        SystemConfig saved = systemConfigRepository.save(config);

        if (admin != null) {
            auditService.logConfigChange(AuditCategory.SYSTEM_CONFIG, AuditAction.CONFIG_UPDATED,
                    admin, key, config.isSensitive() ? "***" : oldValue,
                    config.isSensitive() ? "***" : value);
        }

        log.info("Configuration {} updated by {}", key, admin != null ? admin.getUsername() : "system");
        return SystemConfigDTO.fromEntity(saved);
    }

    /**
     * Create a new configuration entry.
     */
    @Transactional
    public SystemConfigDTO createConfig(String key, String value, String type, String category,
                                         String description, boolean sensitive, boolean readonly, User admin) {
        if (systemConfigRepository.existsByConfigKey(key)) {
            throw new IllegalArgumentException("Configuration key already exists: " + key);
        }

        SystemConfig config = SystemConfig.builder()
                .configKey(key)
                .configValue(value)
                .configType(type)
                .category(category)
                .description(description)
                .sensitive(sensitive)
                .readonly(readonly)
                .updatedBy(admin != null ? admin.getUsername() : "system")
                .build();

        SystemConfig saved = systemConfigRepository.save(config);

        if (admin != null) {
            auditService.log(AuditCategory.SYSTEM_CONFIG, AuditAction.CONFIG_CREATED, admin,
                    "CONFIG", saved.getId(), key,
                    "Created configuration: " + key);
        }

        log.info("Configuration {} created by {}", key, admin != null ? admin.getUsername() : "system");
        return SystemConfigDTO.fromEntity(saved);
    }

    /**
     * Delete a configuration entry.
     */
    @Transactional
    public void deleteConfig(String key, User admin) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Configuration key not found: " + key));

        if (config.isReadonly()) {
            throw new IllegalArgumentException("Configuration is read-only: " + key);
        }

        systemConfigRepository.delete(config);

        if (admin != null) {
            auditService.log(AuditCategory.SYSTEM_CONFIG, AuditAction.CONFIG_DELETED, admin,
                    "CONFIG", config.getId(), key,
                    "Deleted configuration: " + key);
        }

        log.info("Configuration {} deleted by {}", key, admin != null ? admin.getUsername() : "system");
    }

    /**
     * Bulk update configurations.
     */
    @Transactional
    public void bulkUpdate(Map<String, String> configs, User admin) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            try {
                setValue(entry.getKey(), entry.getValue(), admin);
            } catch (Exception e) {
                log.warn("Failed to update config {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * Initialize default configurations if not present.
     */
    @Transactional
    public void initializeDefaults() {
        // System settings
        createIfNotExists("system.name", "Heronix Talk", "STRING", "SYSTEM",
                "System display name", false, false);
        createIfNotExists("system.version", "1.0.0", "STRING", "SYSTEM",
                "System version", false, true);
        createIfNotExists("system.maintenance_mode", "false", "BOOLEAN", "SYSTEM",
                "Enable maintenance mode", false, false);

        // UI settings
        createIfNotExists("ui.theme", "dark", "STRING", "UI",
                "Default UI theme", false, false);
        createIfNotExists("ui.notifications_enabled", "true", "BOOLEAN", "UI",
                "Enable desktop notifications", false, false);
        createIfNotExists("ui.sound_enabled", "true", "BOOLEAN", "UI",
                "Enable notification sounds", false, false);

        // Sync settings
        createIfNotExists("sync.enabled", "true", "BOOLEAN", "SYNC",
                "Enable background sync", false, false);
        createIfNotExists("sync.interval_seconds", "30", "INTEGER", "SYNC",
                "Sync interval in seconds", false, false);

        log.info("System configuration defaults initialized");
    }

    private void createIfNotExists(String key, String value, String type, String category,
                                    String description, boolean sensitive, boolean readonly) {
        if (!systemConfigRepository.existsByConfigKey(key)) {
            SystemConfig config = SystemConfig.builder()
                    .configKey(key)
                    .configValue(value)
                    .configType(type)
                    .category(category)
                    .description(description)
                    .sensitive(sensitive)
                    .readonly(readonly)
                    .updatedBy("system")
                    .build();
            systemConfigRepository.save(config);
        }
    }
}
