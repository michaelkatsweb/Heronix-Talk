package com.heronix.talk.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heronix.talk.config.RateLimitConfig;
import com.heronix.talk.service.RateLimitService;
import com.heronix.talk.service.RateLimitService.RateLimitResult;
import com.heronix.talk.service.RateLimitService.RateLimitType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP filter that applies rate limiting to all API requests.
 * Runs early in the filter chain to reject requests before processing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitConfig rateLimitConfig;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip WebSocket upgrade requests
        if ("websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip static resources and actuator endpoints
        String path = request.getRequestURI();
        if (shouldSkipRateLimiting(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Determine client identifier and rate limit type
        String clientId = getClientId(request);
        RateLimitType limitType = determineRateLimitType(request);

        // Check rate limit
        RateLimitResult result = rateLimitService.tryConsume(clientId, limitType);

        // Add rate limit headers if configured
        if (rateLimitConfig.isIncludeHeaders() && result.getRemainingTokens() >= 0) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitService.getRateLimit(limitType)));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingTokens()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(result.getRetryAfterSeconds()));
        }

        if (result.isAllowed()) {
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));

            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("status", 429);
            errorBody.put("error", "Too Many Requests");
            errorBody.put("message", "Rate limit exceeded. Please try again in " + result.getRetryAfterSeconds() + " seconds.");
            errorBody.put("retryAfter", result.getRetryAfterSeconds());
            errorBody.put("timestamp", LocalDateTime.now().toString());
            errorBody.put("path", path);

            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
        }
    }

    /**
     * Determine if the path should skip rate limiting
     */
    private boolean shouldSkipRateLimiting(String path) {
        return path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/favicon.ico") ||
               path.startsWith("/ws/");
    }

    /**
     * Get a unique identifier for the client
     */
    private String getClientId(HttpServletRequest request) {
        // First try to get user from session token header
        String sessionToken = request.getHeader("X-Session-Token");
        if (sessionToken != null && !sessionToken.isEmpty()) {
            return "user:" + sessionToken.substring(0, Math.min(16, sessionToken.length()));
        }

        // Fall back to IP address
        String clientIp = getClientIp(request);
        return "ip:" + clientIp;
    }

    /**
     * Get the real client IP, considering proxy headers
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Determine the appropriate rate limit type based on the request
     */
    private RateLimitType determineRateLimitType(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String sessionToken = request.getHeader("X-Session-Token");

        // Login and authentication endpoints - strictest limits
        if (path.contains("/auth/login") || path.contains("/auth/register")) {
            return RateLimitType.LOGIN;
        }

        // File upload endpoints
        if (path.contains("/attachments") && "POST".equals(method)) {
            return RateLimitType.UPLOAD;
        }

        // Message sending
        if (path.contains("/messages") && "POST".equals(method)) {
            return RateLimitType.MESSAGE;
        }

        // Admin endpoints
        if (path.startsWith("/api/admin")) {
            return RateLimitType.ADMIN;
        }

        // Unauthenticated requests
        if (sessionToken == null || sessionToken.isEmpty()) {
            return RateLimitType.ANONYMOUS;
        }

        return RateLimitType.DEFAULT;
    }
}
