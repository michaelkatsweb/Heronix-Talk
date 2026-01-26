package com.heronix.talk.model.enums;

/**
 * User roles within the Heronix Talk system.
 * Determines permissions and access levels.
 */
public enum UserRole {
    ADMIN,          // System administrator - full access
    PRINCIPAL,      // School principal - management access
    TEACHER,        // Teacher - standard staff access
    STAFF,          // Non-teaching staff
    COUNSELOR,      // School counselor
    DEPARTMENT_HEAD, // Department head - additional permissions
    STUDENT         // Student - can message teachers and receive announcements
}
