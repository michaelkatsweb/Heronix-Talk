package com.heronix.talk.model.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Network configuration settings for the system.
 */
@Entity
@Table(name = "network_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String name;

    // Server Settings
    @Column(name = "server_host", length = 255)
    @Builder.Default
    private String serverHost = "0.0.0.0";

    @Column(name = "server_port")
    @Builder.Default
    private int serverPort = 9680;

    @Column(name = "ssl_enabled")
    @Builder.Default
    private boolean sslEnabled = false;

    @Column(name = "ssl_cert_path", length = 500)
    private String sslCertPath;

    @Column(name = "ssl_key_path", length = 500)
    private String sslKeyPath;

    @Column(name = "ssl_key_password", length = 255)
    private String sslKeyPassword;

    // Proxy Settings
    @Column(name = "proxy_enabled")
    @Builder.Default
    private boolean proxyEnabled = false;

    @Column(name = "proxy_host", length = 255)
    private String proxyHost;

    @Column(name = "proxy_port")
    private Integer proxyPort;

    @Column(name = "proxy_username", length = 100)
    private String proxyUsername;

    @Column(name = "proxy_password", length = 255)
    private String proxyPassword;

    @Column(name = "proxy_type", length = 20)
    @Builder.Default
    private String proxyType = "HTTP"; // HTTP, SOCKS4, SOCKS5

    // WebSocket Settings
    @Column(name = "websocket_enabled")
    @Builder.Default
    private boolean websocketEnabled = true;

    @Column(name = "websocket_path", length = 100)
    @Builder.Default
    private String websocketPath = "/ws/chat";

    @Column(name = "websocket_heartbeat_interval")
    @Builder.Default
    private int websocketHeartbeatInterval = 30; // seconds

    @Column(name = "websocket_max_message_size")
    @Builder.Default
    private int websocketMaxMessageSize = 65536; // 64KB

    // CORS Settings
    @Column(name = "cors_enabled")
    @Builder.Default
    private boolean corsEnabled = true;

    @Column(name = "cors_allowed_origins", length = 1000)
    @Builder.Default
    private String corsAllowedOrigins = "*";

    @Column(name = "cors_allowed_methods", length = 200)
    @Builder.Default
    private String corsAllowedMethods = "GET,POST,PUT,DELETE,OPTIONS";

    @Column(name = "cors_allowed_headers", length = 500)
    @Builder.Default
    private String corsAllowedHeaders = "*";

    // Connection Settings
    @Column(name = "max_connections")
    @Builder.Default
    private int maxConnections = 1000;

    @Column(name = "connection_timeout")
    @Builder.Default
    private int connectionTimeout = 30000; // ms

    @Column(name = "read_timeout")
    @Builder.Default
    private int readTimeout = 60000; // ms

    @Column(name = "write_timeout")
    @Builder.Default
    private int writeTimeout = 60000; // ms

    // External Service URLs
    @Column(name = "teacher_portal_url", length = 500)
    private String teacherPortalUrl;

    @Column(name = "ldap_server_url", length = 500)
    private String ldapServerUrl;

    @Column(name = "smtp_server_host", length = 255)
    private String smtpServerHost;

    @Column(name = "smtp_server_port")
    private Integer smtpServerPort;

    // Status
    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
