package com.urlshortener.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed analytics for a shortened URL")
public class DetailedAnalytics {

    @Schema(description = "The short key")
    private String shortKey;

    @Schema(description = "The original URL")
    private String originalUrl;

    @Schema(description = "Total number of clicks")
    private Long totalClicks;

    @Schema(description = "When the URL was created")
    private LocalDateTime createdAt;

    @Schema(description = "When the URL expires (null if never)")
    private LocalDateTime expiresAt;

    // Click breakdowns
    @Schema(description = "Clicks by country code")
    private Map<String, Long> clicksByCountry;

    @Schema(description = "Clicks by device type (Desktop, Mobile, Tablet)")
    private Map<String, Long> clicksByDevice;

    @Schema(description = "Clicks by browser")
    private Map<String, Long> clicksByBrowser;

    @Schema(description = "Clicks by operating system")
    private Map<String, Long> clicksByOs;

    @Schema(description = "Clicks by referrer URL")
    private Map<String, Long> clicksByReferer;

    @Schema(description = "Clicks over time (daily)")
    private Map<String, Long> clicksOverTime;

    // AI-generated info
    @Schema(description = "AI-generated summary")
    private String aiSummary;

    @Schema(description = "AI-determined category")
    private String aiCategory;

    @Schema(description = "AI-generated tags (comma-separated)")
    private String aiTags;

    // URL metadata
    @Schema(description = "Page title from metadata")
    private String metaTitle;

    @Schema(description = "Page description from metadata")
    private String metaDescription;

    @Schema(description = "Page image from metadata")
    private String metaImageUrl;
}

