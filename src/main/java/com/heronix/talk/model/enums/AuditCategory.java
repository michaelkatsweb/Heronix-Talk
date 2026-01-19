package com.heronix.talk.model.enums;

/**
 * Categories for audit log entries.
 */
public enum AuditCategory {
    AUTHENTICATION,     // Login, logout, session events
    USER_MANAGEMENT,    // User CRUD operations
    ROLE_MANAGEMENT,    // Role and permission changes
    CHANNEL_MANAGEMENT, // Channel operations
    MESSAGE,            // Message operations (if audited)
    SYSTEM_CONFIG,      // System configuration changes
    SECURITY_POLICY,    // Security policy changes
    NETWORK_CONFIG,     // Network configuration changes
    EMERGENCY,          // Emergency alerts and lockdowns
    DATA_EXPORT,        // Data export operations
    DATA_IMPORT,        // Data import operations
    API_ACCESS,         // API access events
    ADMIN_ACTION        // General admin actions
}
