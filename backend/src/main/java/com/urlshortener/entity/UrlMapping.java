package com.urlshortener.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "url_mapping", indexes = {
        @Index(name = "idx_short_key", columnList = "short_key", unique = true),
        @Index(name = "idx_custom_alias", columnList = "custom_alias", unique = true),
        @Index(name = "idx_ai_category", columnList = "ai_category"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "short_key", length = 20, unique = true)
    private String shortKey;

    @Column(name = "custom_alias", length = 50, unique = true)
    private String customAlias;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Password Protection
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "is_password_protected", nullable = false)
    @Builder.Default
    private Boolean isPasswordProtected = false;

    // AI-Generated Metadata
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "ai_category", length = 100)
    private String aiCategory;

    @Column(name = "ai_tags", columnDefinition = "TEXT")
    private String aiTags;

    @Column(name = "ai_safety_score", precision = 3, scale = 2)
    private BigDecimal aiSafetyScore;

    @Column(name = "ai_analyzed_at")
    private LocalDateTime aiAnalyzedAt;

    // URL Metadata (Open Graph)
    @Column(name = "meta_title", length = 500)
    private String metaTitle;

    @Column(name = "meta_description", columnDefinition = "TEXT")
    private String metaDescription;

    @Column(name = "meta_image_url", columnDefinition = "TEXT")
    private String metaImageUrl;

    @Column(name = "meta_favicon_url", columnDefinition = "TEXT")
    private String metaFaviconUrl;

    @Column(name = "meta_fetched_at")
    private LocalDateTime metaFetchedAt;

    // Creator Info
    @Column(name = "created_by_ip", length = 45)
    private String createdByIp;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (clickCount == null) {
            clickCount = 0L;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (isPasswordProtected == null) {
            isPasswordProtected = false;
        }
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Get the effective key for redirect (custom alias takes priority)
     */
    public String getEffectiveKey() {
        return customAlias != null ? customAlias : shortKey;
    }
}
