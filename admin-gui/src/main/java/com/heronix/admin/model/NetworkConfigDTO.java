package com.heronix.admin.model;

import lombok.Data;

@Data
public class NetworkConfigDTO {
    private Long id;

    // Server settings
    private String serverHost;
    private int serverPort;
    private boolean sslEnabled;
    private String sslKeyStorePath;

    // Proxy settings
    private boolean proxyEnabled;
    private String proxyHost;
    private int proxyPort;
    private String proxyUsername;

    // WebSocket settings
    private int websocketPort;
    private int websocketMaxConnections;
    private int websocketHeartbeatInterval;
    private int websocketMessageMaxSize;

    // CORS settings
    private boolean corsEnabled;
    private String corsAllowedOrigins;
    private String corsAllowedMethods;
    private String corsAllowedHeaders;

    // Rate limiting
    private boolean rateLimitEnabled;
    private int rateLimitRequestsPerMinute;
    private int rateLimitBurstSize;
}
