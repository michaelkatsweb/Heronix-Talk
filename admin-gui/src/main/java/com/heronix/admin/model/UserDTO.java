package com.heronix.admin.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String employeeId;
    private String displayName;
    private String email;
    private String department;
    private String position;
    private String status;
    private String avatarUrl;
    private boolean active;
    private boolean locked;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
    private Set<String> roles;
    private Set<String> permissions;
}
