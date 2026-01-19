package com.heronix.admin.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class RoleDTO {
    private Long id;
    private String name;
    private String displayName;
    private String description;
    private Set<String> permissions;
    private boolean systemRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
