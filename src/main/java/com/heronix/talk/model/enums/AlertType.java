package com.heronix.talk.model.enums;

/**
 * Types of emergency alerts.
 */
public enum AlertType {
    LOCKDOWN,       // Campus lockdown
    FIRE,           // Fire emergency
    WEATHER,        // Severe weather alert
    MEDICAL,        // Medical emergency
    EVACUATION,     // Evacuation required
    SHELTER,        // Shelter in place
    ALL_CLEAR,      // Emergency has ended
    ANNOUNCEMENT,   // General announcement
    SCHEDULE_CHANGE,// Schedule modification
    CUSTOM          // Custom alert type
}
