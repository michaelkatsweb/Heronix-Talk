package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user data received from SIS (Student Information System).
 * Supports multiple field name formats from different SIS systems including Heronix-Application.
 * Uses JsonAlias to map various field naming conventions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SisUserDTO {

    // Employee ID - supports: employeeId, employee_id, empId, id, teacherId, teacher_id
    @JsonAlias({"employee_id", "empId", "emp_id", "id", "teacherId", "teacher_id", "staffId", "staff_id"})
    private String employeeId;

    // Username - supports: username, user_name, loginName, login
    @JsonAlias({"user_name", "loginName", "login_name", "login"})
    private String username;

    // First name - supports: firstName, first_name, fname, givenName
    @JsonAlias({"first_name", "fname", "givenName", "given_name"})
    private String firstName;

    // Last name - supports: lastName, last_name, lname, surname, familyName
    @JsonAlias({"last_name", "lname", "surname", "familyName", "family_name"})
    private String lastName;

    // Email - supports: email, emailAddress, email_address
    @JsonAlias({"emailAddress", "email_address", "mail"})
    private String email;

    // Department - supports: department, dept, departmentName
    @JsonAlias({"dept", "departmentName", "department_name"})
    private String department;

    // Phone - supports: phoneNumber, phone_number, phone, telephone
    @JsonAlias({"phone_number", "phone", "telephone", "tel", "mobile"})
    private String phoneNumber;

    // Role - supports: role, roleName, role_name, position, title
    @JsonAlias({"roleName", "role_name", "position", "title", "jobTitle", "job_title"})
    private String role;

    // Active status - supports: active, isActive, is_active, enabled, status
    @JsonAlias({"isActive", "is_active", "enabled"})
    @Builder.Default
    private boolean active = true;

    // Password hash from SIS (BCrypt) for authentication sync
    @JsonAlias({"passwordHash", "password_hash", "hashedPassword"})
    private String password;

    // Additional fields that might come from Heronix-Application
    @JsonAlias({"full_name", "displayName", "display_name"})
    private String fullName;

    @JsonAlias({"subject", "subjects", "teachingSubject"})
    private String subject;

    public String getFullName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return firstName != null ? firstName : (lastName != null ? lastName : "Unknown");
    }

    public boolean isValid() {
        // Valid if we have an identifier and at least a name
        boolean hasId = (employeeId != null && !employeeId.isBlank()) ||
                        (username != null && !username.isBlank()) ||
                        (email != null && !email.isBlank());
        boolean hasName = (firstName != null && !firstName.isBlank()) ||
                          (lastName != null && !lastName.isBlank()) ||
                          (fullName != null && !fullName.isBlank());
        return hasId && hasName;
    }

    /**
     * Generate employee ID if not present, using other available identifiers.
     */
    public String getEffectiveEmployeeId() {
        if (employeeId != null && !employeeId.isBlank()) {
            return employeeId;
        }
        if (username != null && !username.isBlank()) {
            return "EMP_" + username;
        }
        if (email != null && email.contains("@")) {
            return "EMP_" + email.split("@")[0];
        }
        return null;
    }

    /**
     * Parse full name into first and last names if they're not set.
     */
    public void parseFullNameIfNeeded() {
        if (fullName != null && !fullName.isBlank()) {
            if (firstName == null || firstName.isBlank()) {
                String[] parts = fullName.trim().split("\\s+", 2);
                firstName = parts[0];
                if (parts.length > 1 && (lastName == null || lastName.isBlank())) {
                    lastName = parts[1];
                }
            }
        }
    }
}
