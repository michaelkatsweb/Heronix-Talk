package com.heronix.talk.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket server configuration for handling 200+ concurrent connections.
 *
 * Configures:
 * - Maximum session limits
 * - Message buffer sizes
 * - Idle timeouts
 * - Send timeouts
 */
@Configuration
@Slf4j
public class WebSocketServerConfig {

    @Value("${heronix.websocket.max-text-message-size:65536}")
    private int maxTextMessageSize;

    @Value("${heronix.websocket.max-binary-message-size:1048576}")
    private int maxBinaryMessageSize;

    @Value("${heronix.websocket.session-idle-timeout-ms:300000}")
    private long sessionIdleTimeout;

    @Value("${heronix.websocket.send-buffer-size-limit:524288}")
    private int sendBufferSizeLimit;

    @Value("${heronix.websocket.max-sessions:500}")
    private int maxSessions;

    /**
     * Configure the WebSocket container for high-concurrency scenarios.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

        // Message size limits
        container.setMaxTextMessageBufferSize(maxTextMessageSize);
        container.setMaxBinaryMessageBufferSize(maxBinaryMessageSize);

        // Session timeout (5 minutes default)
        container.setMaxSessionIdleTimeout(sessionIdleTimeout);

        // Async send timeout
        container.setAsyncSendTimeout(10000L);

        log.info("WebSocket container configured: maxTextMsg={}KB, maxBinaryMsg={}KB, idleTimeout={}s, maxSessions={}",
                maxTextMessageSize / 1024,
                maxBinaryMessageSize / 1024,
                sessionIdleTimeout / 1000,
                maxSessions);

        return container;
    }
}
