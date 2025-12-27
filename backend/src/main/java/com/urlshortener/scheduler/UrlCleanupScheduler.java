package com.urlshortener.scheduler;

import com.urlshortener.repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled job for URL cleanup.
 * Runs every hour to mark expired URLs as inactive.
 * Does NOT delete rows (soft delete only).
 */
@Component
public class UrlCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UrlCleanupScheduler.class);

    private final UrlMappingRepository urlMappingRepository;

    @Autowired
    public UrlCleanupScheduler(UrlMappingRepository urlMappingRepository) {
        this.urlMappingRepository = urlMappingRepository;
    }

    /**
     * Cleanup expired URLs every hour.
     * Marks expired URLs as inactive without deleting them.
     */
    @Scheduled(cron = "${app.scheduler.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void cleanupExpiredUrls() {
        log.info("Starting expired URL cleanup job");

        try {
            LocalDateTime now = LocalDateTime.now();
            int updatedCount = urlMappingRepository.markExpiredAsInactive(now);

            log.info("Expired URL cleanup completed. Marked {} URLs as inactive", updatedCount);
        } catch (Exception e) {
            log.error("Error during expired URL cleanup", e);
        }
    }
}

