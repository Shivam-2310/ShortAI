package com.urlshortener.service;

import com.urlshortener.repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Async analytics service for tracking click counts.
 * All operations are non-blocking to ensure redirect latency is not affected.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final UrlMappingRepository urlMappingRepository;

    @Autowired
    public AnalyticsService(UrlMappingRepository urlMappingRepository) {
        this.urlMappingRepository = urlMappingRepository;
    }

    /**
     * Asynchronously increment click count for a short key.
     * This method returns immediately and processes in background.
     *
     * @param shortKey the short key to increment
     */
    @Async("analyticsExecutor")
    @Transactional
    public void incrementClickCountAsync(String shortKey) {
        try {
            urlMappingRepository.incrementClickCount(shortKey);
            log.debug("Incremented click count for key: {}", shortKey);
        } catch (Exception e) {
            log.error("Failed to increment click count for key: {}", shortKey, e);
        }
    }
}

