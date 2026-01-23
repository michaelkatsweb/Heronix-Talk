package com.heronix.talk.config;

import com.heronix.talk.model.dto.ImportResultDTO;
import com.heronix.talk.service.SisSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Performs initial synchronization from SIS when the application starts.
 * This ensures the Talk server has a local copy of all teacher accounts
 * for offline operation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SisStartupSync {

    private final SisSyncService sisSyncService;
    private final SisIntegrationProperties sisProperties;

    @Value("${heronix.sis.startup-sync.enabled:true}")
    private boolean startupSyncEnabled;

    @Value("${heronix.sis.startup-sync.delay-seconds:5}")
    private int startupDelaySeconds;

    /**
     * Triggered when the application is fully started.
     * Performs initial sync from SIS to populate local user database.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        if (!startupSyncEnabled) {
            log.info("SIS startup sync is disabled");
            return;
        }

        if (!sisProperties.getApi().isEnabled()) {
            log.info("SIS API is disabled, skipping startup sync");
            return;
        }

        // Wait a bit for all services to be fully initialized
        try {
            log.info("Waiting {} seconds before initial SIS sync...", startupDelaySeconds);
            Thread.sleep(startupDelaySeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        performStartupSync();
    }

    /**
     * Perform the actual sync from SIS.
     */
    private void performStartupSync() {
        log.info("========================================");
        log.info("   Starting Initial SIS Synchronization");
        log.info("   SIS URL: {}", sisProperties.getApi().getBaseUrl());
        log.info("========================================");

        try {
            // First test the connection
            if (!sisSyncService.testConnection()) {
                log.warn("SIS server not reachable at {}. Will retry on scheduled sync.",
                        sisProperties.getApi().getBaseUrl());
                log.info("Talk server will operate in offline mode until SIS is available.");
                log.info("Users can still auto-register on first login when SIS becomes available.");
                return;
            }

            // Perform the sync
            ImportResultDTO result = sisSyncService.syncFromApi();

            if (result.isSuccess()) {
                log.info("========================================");
                log.info("   SIS Startup Sync Complete");
                log.info("   Total Processed: {}", result.getTotalProcessed());
                log.info("   Created: {}", result.getCreated());
                log.info("   Updated: {}", result.getUpdated());
                log.info("   Skipped: {}", result.getSkipped());
                log.info("   Duration: {} ms", result.getDurationMs());
                log.info("========================================");
            } else {
                log.warn("SIS startup sync completed with errors:");
                result.getErrorMessages().forEach(msg -> log.warn("  - {}", msg));
            }

        } catch (Exception e) {
            log.error("Failed to perform initial SIS sync", e);
            log.info("Talk server will continue in offline mode. Scheduled sync will retry periodically.");
        }
    }
}
