package com.heronix.talk.model.dto;

import lombok.*;

/**
 * Request DTO for updating network configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNetworkConfigRequest {

    // Server Settings
    private String serverHost;
    private Integer serverPort;
    private Boolean sslEnabled;
    private String sslCertPath;
    private String sslKeyPath;
    private String sslKeyPassword;

    // Proxy Settings
    private Boolean proxyEnabled;
    private String proxyHost;
    private Integer proxyPort;
    private String proxyUsername;
    private String proxyPassword;
    private String proxyType;

    // WebSocket Settings
    private Boolean websocketEnabled;
    private String websocketPath;
    private Integer websocketHeartbeatInterval;
    private Integer websocketMaxMessageSize;

    // CORS Settings
    private Boolean corsEnabled;
    private String corsAllowedOrigins;
    private String corsAllowedMethods;
    private String corsAllowedHeaders;

    // Connection Settings
    private Integer maxConnections;
    private Integer connectionTimeout;
    private Integer readTimeout;
    private Integer writeTimeout;

    // External Service URLs
    private String teacherPortalUrl;
    private String ldapServerUrl;
    private String smtpServerHost;
    private Integer smtpServerPort;
}
