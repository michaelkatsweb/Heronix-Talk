package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Authentication response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private boolean success;
    private String message;
    private String sessionToken;
    private UserDTO user;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    public static AuthResponse success(String sessionToken, UserDTO user, LocalDateTime expiresAt) {
        return AuthResponse.builder()
                .success(true)
                .message("Authentication successful")
                .sessionToken(sessionToken)
                .user(user)
                .expiresAt(expiresAt)
                .build();
    }

    public static AuthResponse failure(String message) {
        return AuthResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
