package com.heronix.talk.controller;

import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.NetworkConfigDTO;
import com.heronix.talk.model.dto.UpdateNetworkConfigRequest;
import com.heronix.talk.service.AuthenticationService;
import com.heronix.talk.service.NetworkConfigService;
import com.heronix.talk.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for network configuration management.
 */
@RestController
@RequestMapping("/api/admin/network")
@RequiredArgsConstructor
@Slf4j
public class NetworkController {

    private final NetworkConfigService networkConfigService;
    private final AuthenticationService authenticationService;
    private final UserRoleService userRoleService;

    @GetMapping("/config")
    public ResponseEntity<NetworkConfigDTO> getActiveConfig(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withNetworkAdmin(sessionToken, user -> {
            return ResponseEntity.ok(networkConfigService.getActiveConfigDTO());
        });
    }

    @GetMapping("/configs")
    public ResponseEntity<List<NetworkConfigDTO>> getAllConfigs(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withNetworkAdmin(sessionToken, user -> {
            return ResponseEntity.ok(networkConfigService.getAllConfigs());
        });
    }

    @GetMapping("/config/{id}")
    public ResponseEntity<NetworkConfigDTO> getConfigById(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withNetworkAdmin(sessionToken, user -> {
            return networkConfigService.getConfigById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        });
    }

    @PutMapping("/config/{id}")
    public ResponseEntity<NetworkConfigDTO> updateConfig(
            @PathVariable Long id,
            @RequestBody UpdateNetworkConfigRequest request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withNetworkAdmin(sessionToken, user -> {
            NetworkConfigDTO config = networkConfigService.updateConfig(id, request, user);
            return ResponseEntity.ok(config);
        });
    }

    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withNetworkAdmin(sessionToken, user -> {
            String host = (String) body.get("host");
            int port = ((Number) body.get("port")).intValue();
            int timeout = body.containsKey("timeout") ? ((Number) body.get("timeout")).intValue() : 5000;

            var result = networkConfigService.testConnectivity(host, port, timeout);
            return ResponseEntity.ok(Map.of(
                    "success", result.success(),
                    "message", result.message(),
                    "responseTimeMs", result.responseTimeMs()
            ));
        });
    }

    @PostMapping("/test-service")
    public ResponseEntity<Map<String, Object>> testExternalService(
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withNetworkAdmin(sessionToken, user -> {
            String url = body.get("url");
            var result = networkConfigService.testExternalService(url);
            return ResponseEntity.ok(Map.of(
                    "success", result.success(),
                    "message", result.message(),
                    "responseTimeMs", result.responseTimeMs()
            ));
        });
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getNetworkStatus(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withNetworkAdmin(sessionToken, user -> {
            NetworkConfigDTO config = networkConfigService.getActiveConfigDTO();

            // Test various connections
            boolean serverReachable = true; // Local server always reachable

            Map<String, Object> status = new java.util.HashMap<>();
            status.put("serverHost", config.getServerHost());
            status.put("serverPort", config.getServerPort());
            status.put("sslEnabled", config.isSslEnabled());
            status.put("websocketEnabled", config.isWebsocketEnabled());
            status.put("proxyEnabled", config.isProxyEnabled());

            // Test external services if configured
            if (config.getTeacherPortalUrl() != null && !config.getTeacherPortalUrl().isEmpty()) {
                var portalResult = networkConfigService.testExternalService(config.getTeacherPortalUrl());
                status.put("teacherPortalStatus", Map.of(
                        "url", config.getTeacherPortalUrl(),
                        "reachable", portalResult.success(),
                        "message", portalResult.message()
                ));
            }

            return ResponseEntity.ok(status);
        });
    }

    // ==================== Helper Methods ====================

    private <T> ResponseEntity<T> withNetworkAdmin(String sessionToken, NetworkOperation<T> operation) {
        return authenticationService.getUserFromSession(sessionToken)
                .filter(user -> userRoleService.hasPermission(user, "MANAGE_NETWORK_CONFIG") ||
                               "ADMIN".equalsIgnoreCase(user.getRoleDisplayName()))
                .map(operation::execute)
                .orElse(ResponseEntity.status(403).build());
    }

    @FunctionalInterface
    private interface NetworkOperation<T> {
        ResponseEntity<T> execute(User user);
    }
}
