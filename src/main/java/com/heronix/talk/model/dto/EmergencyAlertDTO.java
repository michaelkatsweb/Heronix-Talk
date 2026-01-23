package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heronix.talk.model.domain.EmergencyAlert;
import com.heronix.talk.model.enums.AlertLevel;
import com.heronix.talk.model.enums.AlertType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for EmergencyAlert entity.
 * Used for broadcasting alerts to all connected clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyAlertDTO {

    private Long id;
    private String alertUuid;
    private String title;
    private String message;
    private String instructions;
    private AlertLevel alertLevel;
    private AlertType alertType;
    private Long issuedById;
    private String issuedByName;
    private boolean active;
    private boolean requiresAcknowledgment;
    private boolean playSound;
    private boolean campusWide;
    private String targetRoles;
    private String targetDepartments;
    private int acknowledgmentCount;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime issuedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cancelledAt;

    public static EmergencyAlertDTO fromEntity(EmergencyAlert alert) {
        if (alert == null) return null;
        return EmergencyAlertDTO.builder()
                .id(alert.getId())
                .alertUuid(alert.getAlertUuid())
                .title(alert.getTitle())
                .message(alert.getMessage())
                .instructions(alert.getInstructions())
                .alertLevel(alert.getAlertLevel())
                .alertType(alert.getAlertType())
                .issuedById(alert.getIssuedBy() != null ? alert.getIssuedBy().getId() : null)
                .issuedByName(alert.getIssuedByName())
                .active(alert.isActive())
                .requiresAcknowledgment(alert.isRequiresAcknowledgment())
                .playSound(alert.isPlaySound())
                .campusWide(alert.isCampusWide())
                .targetRoles(alert.getTargetRoles())
                .targetDepartments(alert.getTargetDepartments())
                .acknowledgmentCount(alert.getAcknowledgmentCount())
                .issuedAt(alert.getIssuedAt())
                .expiresAt(alert.getExpiresAt())
                .cancelledAt(alert.getCancelledAt())
                .build();
    }
}
