package com.heronix.talk.websocket;

import com.heronix.talk.model.domain.User;
import com.heronix.talk.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Interceptor for WebSocket handshake to validate authentication.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final AuthenticationService authenticationService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String sessionToken = servletRequest.getServletRequest().getParameter("token");

            if (sessionToken == null || sessionToken.isEmpty()) {
                // Try header
                sessionToken = servletRequest.getServletRequest().getHeader("X-Session-Token");
            }

            if (sessionToken == null || sessionToken.isEmpty()) {
                log.warn("WebSocket handshake rejected: no session token");
                return false;
            }

            var userOpt = authenticationService.getUserFromSession(sessionToken);
            if (userOpt.isEmpty()) {
                log.warn("WebSocket handshake rejected: invalid session token");
                return false;
            }

            User user = userOpt.get();
            attributes.put("user", user);
            attributes.put("userId", user.getId());
            attributes.put("sessionToken", sessionToken);

            log.info("WebSocket handshake accepted for user: {}", user.getUsername());
            return true;
        }

        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No-op
    }
}
