package com.heronix.talk.model.enums;

/**
 * Types of communication channels.
 */
public enum ChannelType {
    PUBLIC,         // Open to all users (e.g., General, Announcements)
    PRIVATE,        // Invite-only channel
    DEPARTMENT,     // Department-specific channel
    DIRECT_MESSAGE, // One-on-one conversation
    GROUP_MESSAGE,  // Small group conversation (not a formal channel)
    ANNOUNCEMENT    // Read-only announcements channel (admins can post)
}
