package com.urlshortener.repository;

import com.urlshortener.entity.ClickAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickAnalyticsRepository extends JpaRepository<ClickAnalytics, Long> {

    List<ClickAnalytics> findByUrlMappingIdOrderByClickedAtDesc(Long urlMappingId);

    long countByUrlMappingId(Long urlMappingId);

    // Analytics by country - return country name if available, otherwise country code
    @Query("SELECT CASE WHEN c.countryName IS NOT NULL AND c.countryName != '' THEN c.countryName ELSE c.countryCode END, COUNT(c) FROM ClickAnalytics c " +
            "WHERE c.urlMapping.id = :urlMappingId AND c.countryCode IS NOT NULL " +
            "GROUP BY CASE WHEN c.countryName IS NOT NULL AND c.countryName != '' THEN c.countryName ELSE c.countryCode END ORDER BY COUNT(c) DESC")
    List<Object[]> countByCountry(@Param("urlMappingId") Long urlMappingId);

    // Analytics by device type
    @Query("SELECT c.deviceType, COUNT(c) FROM ClickAnalytics c " +
            "WHERE c.urlMapping.id = :urlMappingId AND c.deviceType IS NOT NULL " +
            "GROUP BY c.deviceType ORDER BY COUNT(c) DESC")
    List<Object[]> countByDeviceType(@Param("urlMappingId") Long urlMappingId);

    // Analytics by browser
    @Query("SELECT c.browserName, COUNT(c) FROM ClickAnalytics c " +
            "WHERE c.urlMapping.id = :urlMappingId AND c.browserName IS NOT NULL " +
            "GROUP BY c.browserName ORDER BY COUNT(c) DESC")
    List<Object[]> countByBrowser(@Param("urlMappingId") Long urlMappingId);

    // Analytics by OS
    @Query("SELECT c.osName, COUNT(c) FROM ClickAnalytics c " +
            "WHERE c.urlMapping.id = :urlMappingId AND c.osName IS NOT NULL " +
            "GROUP BY c.osName ORDER BY COUNT(c) DESC")
    List<Object[]> countByOs(@Param("urlMappingId") Long urlMappingId);

    // Clicks over time (daily)
    @Query("SELECT CAST(c.clickedAt AS date), COUNT(c) FROM ClickAnalytics c " +
            "WHERE c.urlMapping.id = :urlMappingId AND c.clickedAt >= :since " +
            "GROUP BY CAST(c.clickedAt AS date) ORDER BY CAST(c.clickedAt AS date)")
    List<Object[]> countByDay(@Param("urlMappingId") Long urlMappingId, @Param("since") LocalDateTime since);

    // Top referrers
    @Query("SELECT c.referer, COUNT(c) FROM ClickAnalytics c " +
            "WHERE c.urlMapping.id = :urlMappingId AND c.referer IS NOT NULL " +
            "GROUP BY c.referer ORDER BY COUNT(c) DESC")
    List<Object[]> countByReferer(@Param("urlMappingId") Long urlMappingId);
}

