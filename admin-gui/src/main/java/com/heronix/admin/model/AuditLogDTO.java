package com.heronix.admin.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLogDTO {
    private Long id;
    private String action;
    private String entityType;
    private Long entityId;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String oldValue;
    private String newValue;
    private String details;
    private LocalDateTime timestamp;
}
