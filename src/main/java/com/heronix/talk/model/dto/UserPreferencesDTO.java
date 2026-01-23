package com.heronix.talk.model.dto;

import lombok.*;

/**
 * DTO for updating user preferences/settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesDTO {

    private Boolean notificationsEnabled;
    private Boolean soundEnabled;
    private String statusMessage;

    // Future extensibility
    private String theme;           // "light", "dark", "system"
    private String language;        // "en", "es", etc.
    private Boolean desktopNotifications;
    private Boolean emailNotifications;
}
