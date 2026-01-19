package com.heronix.admin.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuthResponse {
    private boolean success;
    private String message;
    private String sessionToken;
    private UserDTO user;
    private LocalDateTime expiresAt;
}
