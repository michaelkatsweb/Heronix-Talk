package com.heronix.admin.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SystemConfigDTO {
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    private String category;
    private boolean encrypted;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
