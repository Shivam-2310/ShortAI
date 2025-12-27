package com.urlshortener.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-based rate limiting service using sliding window algorithm.
 * Limits: 100 requests per minute per IP.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String KEY_PREFIX = "rate:";
    private static final int WINDOW_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final int maxRequests;

    @Autowired
    public RateLimitService(
            StringRedisTemplate redisTemplate,
            @Value("${app.rate-limit.requests-per-minute:100}") int maxRequests) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
    }

    /**
     * Check if the request is allowed under rate limit.
     * Uses sliding window counter algorithm.
     *
     * @param clientIp the client IP address
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String clientIp) {
        String key = KEY_PREFIX + clientIp;

        try {
            Long currentCount = redisTemplate.opsForValue().increment(key);

            if (currentCount == null) {
                return true; // Redis error, allow request
            }

            // Set expiry only on first request in window
            if (currentCount == 1) {
                redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            }

            boolean allowed = currentCount <= maxRequests;

            if (!allowed) {
                log.warn("Rate limit exceeded for IP: {}, count: {}", clientIp, currentCount);
            }

            return allowed;
        } catch (Exception e) {
            log.warn("Redis error during rate limiting for IP: {}, allowing request", clientIp, e);
            return true; // Fail open - allow request if Redis is down
        }
    }

    /**
     * Get remaining requests for an IP.
     *
     * @param clientIp the client IP address
     * @return number of remaining requests
     */
    public int getRemainingRequests(String clientIp) {
        String key = KEY_PREFIX + clientIp;

        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return maxRequests;
            }
            int used = Integer.parseInt(value);
            return Math.max(0, maxRequests - used);
        } catch (Exception e) {
            log.warn("Redis error getting remaining requests for IP: {}", clientIp, e);
            return maxRequests;
        }
    }

    /**
     * Get time until rate limit window resets.
     *
     * @param clientIp the client IP address
     * @return seconds until reset, or -1 if no limit active
     */
    public long getResetTimeSeconds(String clientIp) {
        String key = KEY_PREFIX + clientIp;

        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : -1;
        } catch (Exception e) {
            log.warn("Redis error getting TTL for IP: {}", clientIp, e);
            return -1;
        }
    }
}

