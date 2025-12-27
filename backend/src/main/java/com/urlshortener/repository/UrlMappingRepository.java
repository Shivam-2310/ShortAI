package com.urlshortener.repository;

import com.urlshortener.entity.UrlMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortKey(String shortKey);

    Optional<UrlMapping> findByCustomAlias(String customAlias);

    Optional<UrlMapping> findByShortKeyOrCustomAlias(String shortKey, String customAlias);

    Optional<UrlMapping> findByShortKeyAndIsActiveTrue(String shortKey);

    boolean existsByCustomAlias(String customAlias);
    
    boolean existsByShortKey(String shortKey);

    @Modifying
    @Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + 1 WHERE u.shortKey = :shortKey")
    void incrementClickCount(@Param("shortKey") String shortKey);

    @Modifying
    @Query("UPDATE UrlMapping u SET u.isActive = false WHERE u.expiresAt IS NOT NULL AND u.expiresAt < :now AND u.isActive = true")
    int markExpiredAsInactive(@Param("now") LocalDateTime now);

    @Query("SELECT u.originalUrl FROM UrlMapping u WHERE u.shortKey = :shortKey AND u.isActive = true")
    Optional<String> findOriginalUrlByShortKey(@Param("shortKey") String shortKey);

    // Find by AI category
    List<UrlMapping> findByAiCategoryAndIsActiveTrue(String aiCategory);

    // Search URLs
    @Query("SELECT u FROM UrlMapping u WHERE u.isActive = true AND " +
            "(LOWER(u.originalUrl) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.metaTitle) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.aiSummary) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<UrlMapping> searchUrls(@Param("query") String query, Pageable pageable);

    // Get top URLs by clicks
    List<UrlMapping> findTop10ByIsActiveTrueOrderByClickCountDesc();

    // Get recent URLs
    List<UrlMapping> findTop20ByIsActiveTrueOrderByCreatedAtDesc();

    // URLs pending AI analysis
    @Query("SELECT u FROM UrlMapping u WHERE u.aiAnalyzedAt IS NULL AND u.isActive = true ORDER BY u.createdAt DESC")
    List<UrlMapping> findPendingAiAnalysis(Pageable pageable);
}

