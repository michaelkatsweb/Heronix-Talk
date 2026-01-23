package com.heronix.talk.model.enums;

/**
 * Emergency alert priority levels.
 */
public enum AlertLevel {
    EMERGENCY,      // Highest priority - lockdown, fire, etc. (red, audio alert)
    URGENT,         // High priority - weather alert, etc. (orange, audio alert)
    HIGH,           // Important notice requiring attention (yellow)
    NORMAL,         // Regular announcement (blue)
    LOW             // Informational only (gray)
}
