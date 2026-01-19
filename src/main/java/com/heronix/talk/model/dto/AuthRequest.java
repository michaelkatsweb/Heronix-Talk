package com.heronix.talk.model.dto;

import lombok.*;

/**
 * Authentication request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    private String username;
    private String password;
    private String clientType;
    private String clientVersion;
    private String deviceName;
    private boolean rememberMe;
}
