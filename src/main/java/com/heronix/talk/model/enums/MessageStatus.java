package com.heronix.talk.model.enums;

/**
 * Message delivery and read status.
 */
public enum MessageStatus {
    SENDING,        // Message is being sent
    SENT,           // Message delivered to server
    DELIVERED,      // Message delivered to recipient(s)
    READ,           // Message has been read
    FAILED          // Message failed to send
}
