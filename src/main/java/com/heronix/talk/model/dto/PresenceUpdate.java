package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heronix.talk.model.enums.UserStatus;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO for user presence updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceUpdate {
    private Long userId;
    private String userName;
    private UserStatus status;
    private String statusMessage;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSeen;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    public static PresenceUpdate online(Long userId, String userName) {
        return PresenceUpdate.builder()
                .userId(userId)
                .userName(userName)
                .status(UserStatus.ONLINE)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static PresenceUpdate offline(Long userId, String userName) {
        return PresenceUpdate.builder()
                .userId(userId)
                .userName(userName)
                .status(UserStatus.OFFLINE)
                .lastSeen(LocalDateTime.now())
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static PresenceUpdate statusChange(Long userId, String userName, UserStatus status, String statusMessage) {
        return PresenceUpdate.builder()
                .userId(userId)
                .userName(userName)
                .status(status)
                .statusMessage(statusMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
