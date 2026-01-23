package com.heronix.talk.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.heronix.talk.config.RateLimitConfig;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Service for managing API rate limiting using token bucket algorithm.
 * Provides different rate limits based on endpoint type and user authentication status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RateLimitConfig config;

    private Cache<String, TokenBucket> bucketCache;
    private Set<String> whitelistedIps;

    @PostConstruct
    public void init() {
        // Initialize bucket cache with 1-hour expiry
        bucketCache = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(10000)
                .build();

        // Parse whitelisted IPs
        whitelistedIps = Arrays.stream(config.getWhitelistedIps().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        log.info("Rate limiting initialized: enabled={}, defaultLimit={}/min",
                config.isEnabled(), config.getDefaultRequestsPerMinute());
    }

    /**
     * Rate limit categories for different endpoint types
     */
    public enum RateLimitType {
        DEFAULT,
        ANONYMOUS,
        LOGIN,
        MESSAGE,
        UPLOAD,
        ADMIN
    }

    /**
     * Check if a request should be allowed based on rate limiting.
     *
     * @param clientId Unique identifier for the client (IP or user ID)
     * @param type     The type of rate limit to apply
     * @return RateLimitResult containing whether allowed and remaining tokens
     */
    public RateLimitResult tryConsume(String clientId, RateLimitType type) {
        if (!config.isEnabled()) {
            return new RateLimitResult(true, -1, -1);
        }

        if (isWhitelisted(clientId)) {
            return new RateLimitResult(true, -1, -1);
        }

        String bucketKey = clientId + ":" + type.name();
        int limit = getRateLimit(type);

        TokenBucket bucket = bucketCache.get(bucketKey, k -> new TokenBucket(limit));

        boolean allowed = bucket.tryConsume();
        long remaining = bucket.getAvailableTokens();
        long retryAfter = allowed ? 0 : bucket.getSecondsUntilRefill();

        if (!allowed) {
            log.warn("Rate limit exceeded for client: {} type: {} - retry after {} seconds",
                    clientId, type, retryAfter);
        }

        return new RateLimitResult(allowed, remaining, retryAfter);
    }

    /**
     * Check if an IP is whitelisted from rate limiting
     */
    public boolean isWhitelisted(String clientId) {
        return whitelistedIps.contains(clientId);
    }

    /**
     * Get the rate limit for a specific type (for headers)
     */
    public int getRateLimit(RateLimitType type) {
        return switch (type) {
            case ANONYMOUS -> config.getAnonymousRequestsPerMinute();
            case LOGIN -> config.getLoginRequestsPerMinute();
            case MESSAGE -> config.getMessageRequestsPerMinute();
            case UPLOAD -> config.getUploadRequestsPerMinute();
            case ADMIN -> config.getAdminRequestsPerMinute();
            default -> config.getDefaultRequestsPerMinute();
        };
    }

    /**
     * Simple token bucket implementation for rate limiting
     */
    private static class TokenBucket {
        private final int capacity;
        private final AtomicLong tokens;
        private volatile long lastRefillTime;
        private static final long REFILL_INTERVAL_MS = 60_000; // 1 minute

        public TokenBucket(int capacity) {
            this.capacity = capacity;
            this.tokens = new AtomicLong(capacity);
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refillIfNeeded();
            long current = tokens.get();
            if (current > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        public long getAvailableTokens() {
            refillIfNeeded();
            return Math.max(0, tokens.get());
        }

        public long getSecondsUntilRefill() {
            long elapsed = System.currentTimeMillis() - lastRefillTime;
            long remaining = REFILL_INTERVAL_MS - elapsed;
            return Math.max(1, remaining / 1000);
        }

        private void refillIfNeeded() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;

            if (elapsed >= REFILL_INTERVAL_MS) {
                tokens.set(capacity);
                lastRefillTime = now;
            }
        }
    }

    /**
     * Result of a rate limit check
     */
    @Getter
    @RequiredArgsConstructor
    public static class RateLimitResult {
        private final boolean allowed;
        private final long remainingTokens;
        private final long retryAfterSeconds;
    }
}
