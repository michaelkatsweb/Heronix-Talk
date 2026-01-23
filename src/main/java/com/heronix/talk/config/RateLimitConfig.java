package com.heronix.talk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for API rate limiting.
 * Protects against abuse and ensures fair resource usage.
 */
@Configuration
@ConfigurationProperties(prefix = "heronix.ratelimit")
@Getter
@Setter
public class RateLimitConfig {

    /**
     * Enable or disable rate limiting globally
     */
    private boolean enabled = true;

    /**
     * Default requests per minute for authenticated users
     */
    private int defaultRequestsPerMinute = 60;

    /**
     * Requests per minute for unauthenticated/anonymous requests
     */
    private int anonymousRequestsPerMinute = 20;

    /**
     * Requests per minute for login attempts (stricter)
     */
    private int loginRequestsPerMinute = 10;

    /**
     * Requests per minute for message sending
     */
    private int messageRequestsPerMinute = 30;

    /**
     * Requests per minute for file uploads
     */
    private int uploadRequestsPerMinute = 10;

    /**
     * Requests per minute for admin operations
     */
    private int adminRequestsPerMinute = 100;

    /**
     * Whether to include rate limit headers in responses
     */
    private boolean includeHeaders = true;

    /**
     * Whitelist of IPs exempt from rate limiting (comma-separated)
     */
    private String whitelistedIps = "127.0.0.1,::1";
}
