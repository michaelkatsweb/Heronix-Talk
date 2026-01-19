package com.heronix.talk.model.enums;

/**
 * Synchronization status for offline-first operations.
 * Following the Heronix sync pattern.
 */
public enum SyncStatus {
    PENDING,        // Item needs to be synced
    SYNCED,         // Item successfully synced
    CONFLICT,       // Data conflict detected
    LOCAL_ONLY      // Item exists only locally (not yet synced)
}
