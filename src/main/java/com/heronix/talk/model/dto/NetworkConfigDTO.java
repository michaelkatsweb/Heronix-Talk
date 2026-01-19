package com.heronix.talk.model.dto;

import com.heronix.talk.model.domain.NetworkConfig;
import lombok.*;
import java.time.LocalDateTime;

/**
 * DTO for network configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkConfigDTO {

    private Long id;
    private String name;

    // Server Settings
    private String serverHost;
    private int serverPort;
    private boolean sslEnabled;
    private String sslCertPath;
    private String sslKeyPath;

    // Proxy Settings
    private boolean proxyEnabled;
    private String proxyHost;
    private Integer proxyPort;
    private String proxyUsername;
    private String proxyType;

    // WebSocket Settings
    private boolean websocketEnabled;
    private String websocketPath;
    private int websocketHeartbeatInterval;
    private int websocketMaxMessageSize;

    // CORS Settings
    private boolean corsEnabled;
    private String corsAllowedOrigins;
    private String corsAllowedMethods;
    private String corsAllowedHeaders;

    // Connection Settings
    private int maxConnections;
    private int connectionTimeout;
    private int readTimeout;
    private int writeTimeout;

    // External Service URLs
    private String teacherPortalUrl;
    private String ldapServerUrl;
    private String smtpServerHost;
    private Integer smtpServerPort;

    // Status
    private boolean active;
    private LocalDateTime updatedAt;

    public static NetworkConfigDTO fromEntity(NetworkConfig entity) {
        return NetworkConfigDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .serverHost(entity.getServerHost())
                .serverPort(entity.getServerPort())
                .sslEnabled(entity.isSslEnabled())
                .sslCertPath(entity.getSslCertPath())
                .sslKeyPath(entity.getSslKeyPath())
                .proxyEnabled(entity.isProxyEnabled())
                .proxyHost(entity.getProxyHost())
                .proxyPort(entity.getProxyPort())
                .proxyUsername(entity.getProxyUsername())
                .proxyType(entity.getProxyType())
                .websocketEnabled(entity.isWebsocketEnabled())
                .websocketPath(entity.getWebsocketPath())
                .websocketHeartbeatInterval(entity.getWebsocketHeartbeatInterval())
                .websocketMaxMessageSize(entity.getWebsocketMaxMessageSize())
                .corsEnabled(entity.isCorsEnabled())
                .corsAllowedOrigins(entity.getCorsAllowedOrigins())
                .corsAllowedMethods(entity.getCorsAllowedMethods())
                .corsAllowedHeaders(entity.getCorsAllowedHeaders())
                .maxConnections(entity.getMaxConnections())
                .connectionTimeout(entity.getConnectionTimeout())
                .readTimeout(entity.getReadTimeout())
                .writeTimeout(entity.getWriteTimeout())
                .teacherPortalUrl(entity.getTeacherPortalUrl())
                .ldapServerUrl(entity.getLdapServerUrl())
                .smtpServerHost(entity.getSmtpServerHost())
                .smtpServerPort(entity.getSmtpServerPort())
                .active(entity.isActive())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
