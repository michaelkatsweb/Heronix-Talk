package com.heronix.talk.model.enums;

/**
 * Types of messages in the system.
 */
public enum MessageType {
    TEXT,           // Regular text message
    FILE,           // File attachment
    IMAGE,          // Image attachment
    SYSTEM,         // System-generated message (user joined, etc.)
    ANNOUNCEMENT,   // Important announcement (highlighted)
    REPLY,          // Reply to another message
    REACTION,       // Emoji reaction to a message
    EDITED,         // Indicates message was edited
    DELETED         // Placeholder for deleted message
}
