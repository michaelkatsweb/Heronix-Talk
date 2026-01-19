package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.enums.UserStatus;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for User entity.
 * Used for API communication and WebSocket messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private String username;
    private String employeeId;
    private String firstName;
    private String lastName;
    private String email;
    private String department;
    private String phoneNumber;
    private String role;
    private UserStatus status;
    private String statusMessage;
    private String avatarPath;
    private boolean active;
    private boolean notificationsEnabled;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSeen;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastActivity;

    public static UserDTO fromEntity(User user) {
        if (user == null) return null;
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .employeeId(user.getEmployeeId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .department(user.getDepartment())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .status(user.getStatus())
                .statusMessage(user.getStatusMessage())
                .avatarPath(user.getAvatarPath())
                .active(user.isActive())
                .notificationsEnabled(user.isNotificationsEnabled())
                .lastSeen(user.getLastSeen())
                .lastActivity(user.getLastActivity())
                .build();
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isOnline() {
        return status != null && status != UserStatus.OFFLINE;
    }
}
