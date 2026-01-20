package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user data received from SIS (Student Information System).
 * Maps to the staff/teacher export format from Heronix-SIS.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SisUserDTO {

    private String employeeId;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String department;
    private String phoneNumber;
    private String role;
    private boolean active;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isValid() {
        return employeeId != null && !employeeId.isBlank()
                && firstName != null && !firstName.isBlank()
                && lastName != null && !lastName.isBlank();
    }
}
