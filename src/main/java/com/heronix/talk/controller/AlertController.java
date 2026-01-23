package com.heronix.talk.controller;

import com.heronix.talk.model.dto.EmergencyAlertDTO;
import com.heronix.talk.model.enums.AlertLevel;
import com.heronix.talk.model.enums.AlertType;
import com.heronix.talk.service.AlertService;
import com.heronix.talk.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for emergency alert operations.
 * Provides endpoints for creating, managing, and retrieving campus-wide alerts.
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Slf4j
public class AlertController {

    private final AlertService alertService;
    private final AuthenticationService authenticationService;

    /**
     * Get all active alerts.
     */
    @GetMapping
    public ResponseEntity<List<EmergencyAlertDTO>> getActiveAlerts(
            @RequestParam(required = false) Integer limit) {
        if (limit != null && limit > 0) {
            return ResponseEntity.ok(alertService.getActiveAlerts(limit));
        }
        return ResponseEntity.ok(alertService.getActiveAlerts());
    }

    /**
     * Get critical alerts only (EMERGENCY and URGENT levels).
     */
    @GetMapping("/critical")
    public ResponseEntity<List<EmergencyAlertDTO>> getCriticalAlerts() {
        return ResponseEntity.ok(alertService.getCriticalAlerts());
    }

    /**
     * Get alerts by level.
     */
    @GetMapping("/level/{level}")
    public ResponseEntity<List<EmergencyAlertDTO>> getAlertsByLevel(@PathVariable String level) {
        try {
            AlertLevel alertLevel = AlertLevel.valueOf(level.toUpperCase());
            return ResponseEntity.ok(alertService.getAlertsByLevel(alertLevel));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get alerts by type.
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<EmergencyAlertDTO>> getAlertsByType(@PathVariable String type) {
        try {
            AlertType alertType = AlertType.valueOf(type.toUpperCase());
            return ResponseEntity.ok(alertService.getAlertsByType(alertType));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create and broadcast a new alert.
     * Request body should contain: title, message, instructions (optional),
     * alertLevel, alertType, requiresAcknowledgment, playSound
     */
    @PostMapping
    public ResponseEntity<EmergencyAlertDTO> createAlert(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    String title = (String) request.get("title");
                    String message = (String) request.get("message");
                    String instructions = (String) request.get("instructions");

                    AlertLevel level = AlertLevel.valueOf(
                            ((String) request.getOrDefault("alertLevel", "NORMAL")).toUpperCase());
                    AlertType type = AlertType.valueOf(
                            ((String) request.getOrDefault("alertType", "ANNOUNCEMENT")).toUpperCase());

                    boolean requiresAck = Boolean.TRUE.equals(request.get("requiresAcknowledgment"));
                    boolean playSound = request.get("playSound") == null || Boolean.TRUE.equals(request.get("playSound"));

                    var alert = alertService.createAlert(
                            title, message, instructions, level, type, user, requiresAck, playSound);

                    log.info("Alert created by {}: [{}] {}", user.getUsername(), level, title);
                    return ResponseEntity.ok(EmergencyAlertDTO.fromEntity(alert));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Create and broadcast an emergency-level alert (fast track).
     */
    @PostMapping("/emergency")
    public ResponseEntity<EmergencyAlertDTO> createEmergencyAlert(
            @RequestBody Map<String, String> request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    String title = request.get("title");
                    String message = request.get("message");
                    AlertType type = AlertType.valueOf(
                            request.getOrDefault("alertType", "ANNOUNCEMENT").toUpperCase());

                    var alert = alertService.createEmergencyAlert(title, message, type, user);

                    log.warn("EMERGENCY ALERT created by {}: {}", user.getUsername(), title);
                    return ResponseEntity.ok(EmergencyAlertDTO.fromEntity(alert));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Create and broadcast an urgent-level alert.
     */
    @PostMapping("/urgent")
    public ResponseEntity<EmergencyAlertDTO> createUrgentAlert(
            @RequestBody Map<String, String> request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    String title = request.get("title");
                    String message = request.get("message");
                    AlertType type = AlertType.valueOf(
                            request.getOrDefault("alertType", "ANNOUNCEMENT").toUpperCase());

                    var alert = alertService.createUrgentAlert(title, message, type, user);

                    log.warn("URGENT ALERT created by {}: {}", user.getUsername(), title);
                    return ResponseEntity.ok(EmergencyAlertDTO.fromEntity(alert));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Issue ALL CLEAR - cancels all active emergencies.
     */
    @PostMapping("/all-clear")
    public ResponseEntity<EmergencyAlertDTO> issueAllClear(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    var allClear = alertService.issueAllClear(user);
                    log.warn("ALL CLEAR issued by {}", user.getUsername());
                    return ResponseEntity.ok(EmergencyAlertDTO.fromEntity(allClear));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Cancel a specific alert.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelAlert(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    alertService.cancelAlert(id);
                    log.info("Alert {} cancelled by {}", id, user.getUsername());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Cancel alert by UUID (for client sync).
     */
    @PostMapping("/uuid/{uuid}/cancel")
    public ResponseEntity<Void> cancelAlertByUuid(
            @PathVariable String uuid,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    alertService.cancelAlertByUuid(uuid);
                    log.info("Alert {} cancelled by UUID by {}", uuid, user.getUsername());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Acknowledge an alert (for acknowledgment tracking).
     */
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Void> acknowledgeAlert(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    alertService.acknowledgeAlert(id);
                    log.debug("Alert {} acknowledged by {}", id, user.getUsername());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Get alert counts for dashboard.
     */
    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> getAlertCounts() {
        return ResponseEntity.ok(Map.of(
                "active", alertService.getActiveAlertCount(),
                "critical", alertService.getCriticalAlertCount()
        ));
    }
}
