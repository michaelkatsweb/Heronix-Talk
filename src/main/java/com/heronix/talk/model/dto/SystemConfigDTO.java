package com.heronix.talk.model.dto;

import com.heronix.talk.model.domain.SystemConfig;
import lombok.*;
import java.time.LocalDateTime;

/**
 * DTO for system configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigDTO {

    private Long id;
    private String configKey;
    private String configValue;
    private String configType;
    private String description;
    private String category;
    private boolean sensitive;
    private boolean readonly;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public static SystemConfigDTO fromEntity(SystemConfig entity) {
        return SystemConfigDTO.builder()
                .id(entity.getId())
                .configKey(entity.getConfigKey())
                .configValue(entity.isSensitive() ? "********" : entity.getConfigValue())
                .configType(entity.getConfigType())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .sensitive(entity.isSensitive())
                .readonly(entity.isReadonly())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }
}
