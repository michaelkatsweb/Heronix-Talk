package com.heronix.talk.model.dto;

import lombok.*;

/**
 * Request DTO for admin password reset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {

    private Long userId;
    private String newPassword;
    private boolean forceChangeOnLogin;
    private boolean sendNotification;
    private String resetReason;
}
