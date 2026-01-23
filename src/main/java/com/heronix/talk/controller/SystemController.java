package com.heronix.talk.controller;

import com.heronix.talk.service.NewsService;
import com.heronix.talk.service.PresenceService;
import com.heronix.talk.service.UserService;
import com.heronix.talk.websocket.ChatWebSocketHandler;
import com.heronix.talk.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for system status and health checks.
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Slf4j
public class SystemController {

    private final UserService userService;
    private final PresenceService presenceService;
    private final NewsService newsService;
    private final ChatWebSocketHandler webSocketHandler;
    private final WebSocketSessionManager sessionManager;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port}")
    private int serverPort;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("application", applicationName);
        return ResponseEntity.ok(health);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> status = new HashMap<>();

        status.put("application", applicationName);
        status.put("port", serverPort);
        status.put("timestamp", LocalDateTime.now());

        // User statistics
        Map<String, Object> users = new HashMap<>();
        users.put("total", userService.getTotalActiveUserCount());
        users.put("online", presenceService.getOnlineUserCount());
        users.put("websocketConnections", webSocketHandler.getActiveConnectionCount());
        status.put("users", users);

        // WebSocket session statistics
        status.put("websocket", sessionManager.getStatistics());

        // News statistics
        Map<String, Object> news = new HashMap<>();
        news.put("active", newsService.getActiveNewsCount());
        news.put("urgent", newsService.getUrgentNewsCount());
        status.put("news", news);

        status.put("status", "RUNNING");

        return ResponseEntity.ok(status);
    }

    @GetMapping("/metrics/websocket")
    public ResponseEntity<Map<String, Object>> websocketMetrics() {
        return ResponseEntity.ok(sessionManager.getStatistics());
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "Heronix Talk");
        info.put("description", "Offline-First Chat Messaging Server");
        info.put("version", "1.0.0-SNAPSHOT");
        info.put("philosophy", "Heronix - Offline First, No Web Dependencies");

        Map<String, Object> features = new HashMap<>();
        features.put("realTimeMessaging", true);
        features.put("channels", true);
        features.put("directMessages", true);
        features.put("fileAttachments", true);
        features.put("newsTicker", true);
        features.put("presence", true);
        features.put("typingIndicators", true);
        features.put("reactions", true);
        info.put("features", features);

        Map<String, Object> endpoints = new HashMap<>();
        endpoints.put("websocket", "/ws/chat");
        endpoints.put("api", "/api");
        endpoints.put("h2Console", "/h2-console");
        info.put("endpoints", endpoints);

        return ResponseEntity.ok(info);
    }
}
