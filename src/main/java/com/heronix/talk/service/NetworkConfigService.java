package com.heronix.talk.service;

import com.heronix.talk.model.domain.NetworkConfig;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.NetworkConfigDTO;
import com.heronix.talk.model.dto.UpdateNetworkConfigRequest;
import com.heronix.talk.model.enums.AuditAction;
import com.heronix.talk.model.enums.AuditCategory;
import com.heronix.talk.repository.NetworkConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for network configuration management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkConfigService {

    private final NetworkConfigRepository networkConfigRepository;
    private final AuditService auditService;

    /**
     * Get the active network configuration.
     */
    public NetworkConfig getActiveConfig() {
        return networkConfigRepository.findFirstByActiveTrue()
                .orElseGet(this::createDefaultConfig);
    }

    public NetworkConfigDTO getActiveConfigDTO() {
        return NetworkConfigDTO.fromEntity(getActiveConfig());
    }

    public List<NetworkConfigDTO> getAllConfigs() {
        return networkConfigRepository.findAll().stream()
                .map(NetworkConfigDTO::fromEntity)
                .toList();
    }

    public Optional<NetworkConfigDTO> getConfigById(Long id) {
        return networkConfigRepository.findById(id)
                .map(NetworkConfigDTO::fromEntity);
    }

    /**
     * Update network configuration.
     */
    @Transactional
    public NetworkConfigDTO updateConfig(Long id, UpdateNetworkConfigRequest request, User admin) {
        NetworkConfig config = networkConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Network config not found"));

        StringBuilder changes = new StringBuilder();

        // Server Settings
        if (request.getServerHost() != null && !request.getServerHost().equals(config.getServerHost())) {
            changes.append("serverHost: ").append(config.getServerHost()).append(" -> ").append(request.getServerHost()).append("; ");
            config.setServerHost(request.getServerHost());
        }
        if (request.getServerPort() != null && request.getServerPort() != config.getServerPort()) {
            changes.append("serverPort: ").append(config.getServerPort()).append(" -> ").append(request.getServerPort()).append("; ");
            config.setServerPort(request.getServerPort());
        }
        if (request.getSslEnabled() != null) {
            config.setSslEnabled(request.getSslEnabled());
        }
        if (request.getSslCertPath() != null) {
            config.setSslCertPath(request.getSslCertPath());
        }
        if (request.getSslKeyPath() != null) {
            config.setSslKeyPath(request.getSslKeyPath());
        }
        if (request.getSslKeyPassword() != null) {
            config.setSslKeyPassword(request.getSslKeyPassword());
        }

        // Proxy Settings
        if (request.getProxyEnabled() != null) {
            config.setProxyEnabled(request.getProxyEnabled());
        }
        if (request.getProxyHost() != null) {
            config.setProxyHost(request.getProxyHost());
        }
        if (request.getProxyPort() != null) {
            config.setProxyPort(request.getProxyPort());
        }
        if (request.getProxyUsername() != null) {
            config.setProxyUsername(request.getProxyUsername());
        }
        if (request.getProxyPassword() != null) {
            config.setProxyPassword(request.getProxyPassword());
        }
        if (request.getProxyType() != null) {
            config.setProxyType(request.getProxyType());
        }

        // WebSocket Settings
        if (request.getWebsocketEnabled() != null) {
            config.setWebsocketEnabled(request.getWebsocketEnabled());
        }
        if (request.getWebsocketPath() != null) {
            config.setWebsocketPath(request.getWebsocketPath());
        }
        if (request.getWebsocketHeartbeatInterval() != null) {
            config.setWebsocketHeartbeatInterval(request.getWebsocketHeartbeatInterval());
        }
        if (request.getWebsocketMaxMessageSize() != null) {
            config.setWebsocketMaxMessageSize(request.getWebsocketMaxMessageSize());
        }

        // CORS Settings
        if (request.getCorsEnabled() != null) {
            config.setCorsEnabled(request.getCorsEnabled());
        }
        if (request.getCorsAllowedOrigins() != null) {
            config.setCorsAllowedOrigins(request.getCorsAllowedOrigins());
        }
        if (request.getCorsAllowedMethods() != null) {
            config.setCorsAllowedMethods(request.getCorsAllowedMethods());
        }
        if (request.getCorsAllowedHeaders() != null) {
            config.setCorsAllowedHeaders(request.getCorsAllowedHeaders());
        }

        // Connection Settings
        if (request.getMaxConnections() != null) {
            config.setMaxConnections(request.getMaxConnections());
        }
        if (request.getConnectionTimeout() != null) {
            config.setConnectionTimeout(request.getConnectionTimeout());
        }
        if (request.getReadTimeout() != null) {
            config.setReadTimeout(request.getReadTimeout());
        }
        if (request.getWriteTimeout() != null) {
            config.setWriteTimeout(request.getWriteTimeout());
        }

        // External Service URLs
        if (request.getTeacherPortalUrl() != null) {
            config.setTeacherPortalUrl(request.getTeacherPortalUrl());
        }
        if (request.getLdapServerUrl() != null) {
            config.setLdapServerUrl(request.getLdapServerUrl());
        }
        if (request.getSmtpServerHost() != null) {
            config.setSmtpServerHost(request.getSmtpServerHost());
        }
        if (request.getSmtpServerPort() != null) {
            config.setSmtpServerPort(request.getSmtpServerPort());
        }

        NetworkConfig saved = networkConfigRepository.save(config);

        auditService.log(AuditCategory.NETWORK_CONFIG, AuditAction.NETWORK_CONFIG_UPDATED, admin,
                "NETWORK_CONFIG", saved.getId(), saved.getName(),
                "Network configuration updated: " + (changes.length() > 0 ? changes.toString() : "various settings"));

        log.info("Network configuration {} updated by {}", saved.getName(), admin.getUsername());
        return NetworkConfigDTO.fromEntity(saved);
    }

    /**
     * Test network connectivity.
     */
    public NetworkTestResult testConnectivity(String host, int port, int timeoutMs) {
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            socket.close();
            return new NetworkTestResult(true, "Connection successful", System.currentTimeMillis());
        } catch (Exception e) {
            return new NetworkTestResult(false, "Connection failed: " + e.getMessage(), 0);
        }
    }

    /**
     * Test external service connectivity.
     */
    public NetworkTestResult testExternalService(String url) {
        try {
            java.net.URL serviceUrl = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) serviceUrl.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            long startTime = System.currentTimeMillis();
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            connection.disconnect();

            if (responseCode >= 200 && responseCode < 400) {
                return new NetworkTestResult(true, "Service reachable (HTTP " + responseCode + ")", responseTime);
            } else {
                return new NetworkTestResult(false, "Service returned HTTP " + responseCode, responseTime);
            }
        } catch (Exception e) {
            return new NetworkTestResult(false, "Service unreachable: " + e.getMessage(), 0);
        }
    }

    private NetworkConfig createDefaultConfig() {
        NetworkConfig config = NetworkConfig.builder()
                .name("default")
                .active(true)
                .build();
        return networkConfigRepository.save(config);
    }

    /**
     * Network test result.
     */
    public record NetworkTestResult(boolean success, String message, long responseTimeMs) {}
}
