package com.heronix.talk.service;

import com.heronix.talk.model.domain.EmergencyAlert;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.ChatWsMessage;
import com.heronix.talk.model.dto.EmergencyAlertDTO;
import com.heronix.talk.model.enums.AlertLevel;
import com.heronix.talk.model.enums.AlertType;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.repository.EmergencyAlertRepository;
import com.heronix.talk.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for emergency alert operations.
 * Handles creation, broadcasting, and management of campus-wide alerts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final EmergencyAlertRepository alertRepository;
    private final ChatWebSocketHandler webSocketHandler;

    public Optional<EmergencyAlert> findById(Long id) {
        return alertRepository.findById(id);
    }

    public Optional<EmergencyAlert> findByUuid(String uuid) {
        return alertRepository.findByAlertUuid(uuid);
    }

    /**
     * Get all currently active alerts.
     */
    public List<EmergencyAlertDTO> getActiveAlerts() {
        return alertRepository.findActiveAlerts(LocalDateTime.now()).stream()
                .map(EmergencyAlertDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get active alerts with pagination.
     */
    public List<EmergencyAlertDTO> getActiveAlerts(int limit) {
        return alertRepository.findActiveAlertsPaged(LocalDateTime.now(), PageRequest.of(0, limit))
                .getContent().stream()
                .map(EmergencyAlertDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get critical alerts (EMERGENCY and URGENT levels only).
     */
    public List<EmergencyAlertDTO> getCriticalAlerts() {
        return alertRepository.findCriticalAlerts(LocalDateTime.now()).stream()
                .map(EmergencyAlertDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get alerts by level.
     */
    public List<EmergencyAlertDTO> getAlertsByLevel(AlertLevel level) {
        return alertRepository.findActiveByLevel(level, LocalDateTime.now()).stream()
                .map(EmergencyAlertDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get alerts by type.
     */
    public List<EmergencyAlertDTO> getAlertsByType(AlertType type) {
        return alertRepository.findActiveByType(type, LocalDateTime.now()).stream()
                .map(EmergencyAlertDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Create and broadcast a new emergency alert.
     */
    @Transactional
    public EmergencyAlert createAlert(String title, String message, String instructions,
                                       AlertLevel level, AlertType type, User issuedBy,
                                       boolean requiresAck, boolean playSound) {
        log.warn("ALERT ISSUED: [{}] {} - {} by {}", level, type, title, issuedBy.getUsername());

        EmergencyAlert alert = EmergencyAlert.builder()
                .title(title)
                .message(message)
                .instructions(instructions)
                .alertLevel(level)
                .alertType(type)
                .issuedBy(issuedBy)
                .issuedAt(LocalDateTime.now())
                .requiresAcknowledgment(requiresAck)
                .playSound(playSound)
                .campusWide(true)
                .syncStatus(SyncStatus.LOCAL_ONLY)
                .build();

        EmergencyAlert saved = alertRepository.save(alert);

        // Broadcast to ALL connected clients immediately
        broadcastAlert(saved, ChatWsMessage.ACTION_CREATE);

        return saved;
    }

    /**
     * Create and broadcast an emergency-level alert (fastest path for critical emergencies).
     */
    @Transactional
    public EmergencyAlert createEmergencyAlert(String title, String message, AlertType type, User issuedBy) {
        return createAlert(title, message, null, AlertLevel.EMERGENCY, type, issuedBy, true, true);
    }

    /**
     * Create and broadcast an urgent-level alert.
     */
    @Transactional
    public EmergencyAlert createUrgentAlert(String title, String message, AlertType type, User issuedBy) {
        return createAlert(title, message, null, AlertLevel.URGENT, type, issuedBy, false, true);
    }

    /**
     * Cancel a specific alert.
     */
    @Transactional
    public void cancelAlert(Long alertId) {
        alertRepository.findById(alertId).ifPresent(alert -> {
            alert.cancel();
            alertRepository.save(alert);
            log.info("Alert {} cancelled", alertId);

            // Broadcast cancellation to all clients
            broadcastAlert(alert, ChatWsMessage.ACTION_DELETE);
        });
    }

    /**
     * Cancel alert by UUID (for sync from clients).
     */
    @Transactional
    public void cancelAlertByUuid(String uuid) {
        alertRepository.findByAlertUuid(uuid).ifPresent(alert -> {
            alert.cancel();
            alertRepository.save(alert);
            log.info("Alert {} cancelled by UUID", uuid);

            // Broadcast cancellation to all clients
            broadcastAlert(alert, ChatWsMessage.ACTION_DELETE);
        });
    }

    /**
     * Issue ALL CLEAR - cancels all active emergency and urgent alerts.
     */
    @Transactional
    public EmergencyAlert issueAllClear(User issuedBy) {
        log.warn("ALL CLEAR issued by {}", issuedBy.getUsername());

        // Cancel all active emergencies
        int cancelled = alertRepository.cancelAllEmergencies(LocalDateTime.now());
        log.info("Cancelled {} active emergency alerts", cancelled);

        // Create ALL_CLEAR notification
        EmergencyAlert allClear = EmergencyAlert.builder()
                .title("All Clear")
                .message("The emergency situation has ended. Normal operations may resume.")
                .alertLevel(AlertLevel.NORMAL)
                .alertType(AlertType.ALL_CLEAR)
                .issuedBy(issuedBy)
                .issuedAt(LocalDateTime.now())
                .playSound(true)
                .campusWide(true)
                .syncStatus(SyncStatus.LOCAL_ONLY)
                .build();

        EmergencyAlert saved = alertRepository.save(allClear);

        // Broadcast ALL CLEAR to everyone
        broadcastAlert(saved, "ALL_CLEAR");

        return saved;
    }

    /**
     * Record acknowledgment of an alert by a user.
     */
    @Transactional
    public void acknowledgeAlert(Long alertId) {
        alertRepository.incrementAcknowledgment(alertId);
    }

    /**
     * Cleanup expired alerts.
     */
    @Transactional
    public int cleanupExpiredAlerts() {
        int deactivated = alertRepository.deactivateExpired(LocalDateTime.now());
        if (deactivated > 0) {
            log.info("Deactivated {} expired alerts", deactivated);
        }
        return deactivated;
    }

    /**
     * Get counts for dashboard.
     */
    public long getActiveAlertCount() {
        return alertRepository.countByActiveTrue();
    }

    public long getCriticalAlertCount() {
        return alertRepository.countCriticalActive(LocalDateTime.now());
    }

    /**
     * Broadcast an alert to all connected WebSocket clients.
     */
    private void broadcastAlert(EmergencyAlert alert, String action) {
        EmergencyAlertDTO dto = EmergencyAlertDTO.fromEntity(alert);
        webSocketHandler.broadcastAlert(dto, action);
    }
}
