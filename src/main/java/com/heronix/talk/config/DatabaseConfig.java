package com.heronix.talk.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.File;

/**
 * Database configuration for Heronix Talk.
 * Supports H2 (offline-first) and PostgreSQL (enterprise).
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.heronix.talk.repository")
@EnableTransactionManagement
@Slf4j
public class DatabaseConfig {

    @Value("${heronix.database.data-dir:./data}")
    private String dataDirectory;

    @Value("${spring.profiles.active:h2}")
    private String activeProfile;

    @PostConstruct
    public void initialize() {
        // Create data directory if using H2
        if ("h2".equals(activeProfile) || activeProfile.contains("h2")) {
            File dataDir = new File(dataDirectory);
            if (!dataDir.exists()) {
                boolean created = dataDir.mkdirs();
                if (created) {
                    log.info("Created data directory: {}", dataDir.getAbsolutePath());
                }
            }
            log.info("Heronix Talk using H2 database at: {}", dataDir.getAbsolutePath());
        } else if ("postgresql".equals(activeProfile) || activeProfile.contains("postgresql")) {
            log.info("Heronix Talk using PostgreSQL database (enterprise mode)");
        }

        log.info("========================================");
        log.info("   Heronix Talk Database Initialized   ");
        log.info("   Profile: {}                         ", activeProfile);
        log.info("========================================");
    }
}
