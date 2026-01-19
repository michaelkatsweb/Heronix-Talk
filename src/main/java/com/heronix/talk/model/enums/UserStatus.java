package com.heronix.talk.model.enums;

/**
 * User online status for presence tracking.
 */
public enum UserStatus {
    ONLINE,         // User is active and available
    AWAY,           // User is away (auto-set after inactivity)
    BUSY,           // User is busy (do not disturb)
    IN_CLASS,       // Teacher is currently in class
    IN_MEETING,     // User is in a meeting
    OFFLINE         // User is not connected
}
