package com.urlshortener.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis cache service for URL mappings.
 * Key format: short:{shortKey} → originalUrl
 * TTL: 24 hours with LRU eviction
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);
    private static final String KEY_PREFIX = "short:";

    private final StringRedisTemplate redisTemplate;
    private final int ttlHours;

    @Autowired
    public CacheService(
            StringRedisTemplate redisTemplate,
            @Value("${app.cache.ttl-hours:24}") int ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttlHours = ttlHours;
    }

    /**
     * Get URL from cache.
     *
     * @param shortKey the short key
     * @return Optional containing the original URL if found
     */
    public Optional<String> get(String shortKey) {
        try {
            String key = KEY_PREFIX + shortKey;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                log.debug("Cache HIT for key: {}", shortKey);
                return Optional.of(value);
            }
            log.debug("Cache MISS for key: {}", shortKey);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis error on GET for key: {}, falling back to DB", shortKey, e);
            return Optional.empty();
        }
    }

    /**
     * Put URL in cache with TTL.
     *
     * @param shortKey    the short key
     * @param originalUrl the original URL
     */
    public void put(String shortKey, String originalUrl) {
        try {
            String key = KEY_PREFIX + shortKey;
            redisTemplate.opsForValue().set(key, originalUrl, ttlHours, TimeUnit.HOURS);
            log.debug("Cached URL for key: {} with TTL: {} hours", shortKey, ttlHours);
        } catch (Exception e) {
            log.warn("Redis error on PUT for key: {}", shortKey, e);
        }
    }

    /**
     * Invalidate cache entry.
     *
     * @param shortKey the short key
     */
    public void invalidate(String shortKey) {
        try {
            String key = KEY_PREFIX + shortKey;
            redisTemplate.delete(key);
            log.debug("Invalidated cache for key: {}", shortKey);
        } catch (Exception e) {
            log.warn("Redis error on DELETE for key: {}", shortKey, e);
        }
    }

    /**
     * Check if Redis is available.
     *
     * @return true if Redis is accessible
     */
    public boolean isAvailable() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            log.warn("Redis is not available", e);
            return false;
        }
    }
}

