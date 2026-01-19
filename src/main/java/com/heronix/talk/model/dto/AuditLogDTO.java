package com.heronix.talk.model.dto;

import com.heronix.talk.model.domain.AuditLog;
import com.heronix.talk.model.enums.AuditAction;
import com.heronix.talk.model.enums.AuditCategory;
import lombok.*;
import java.time.LocalDateTime;

/**
 * DTO for audit log entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {

    private Long id;
    private LocalDateTime timestamp;
    private AuditCategory category;
    private AuditAction action;
    private Long userId;
    private String username;
    private String targetType;
    private Long targetId;
    private String targetName;
    private String description;
    private String ipAddress;
    private String requestMethod;
    private String requestPath;
    private Integer responseStatus;
    private Long executionTimeMs;
    private boolean success;
    private String errorMessage;

    public static AuditLogDTO fromEntity(AuditLog entity) {
        return AuditLogDTO.builder()
                .id(entity.getId())
                .timestamp(entity.getTimestamp())
                .category(entity.getCategory())
                .action(entity.getAction())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .targetName(entity.getTargetName())
                .description(entity.getDescription())
                .ipAddress(entity.getIpAddress())
                .requestMethod(entity.getRequestMethod())
                .requestPath(entity.getRequestPath())
                .responseStatus(entity.getResponseStatus())
                .executionTimeMs(entity.getExecutionTimeMs())
                .success(entity.isSuccess())
                .errorMessage(entity.getErrorMessage())
                .build();
    }
}
