package com.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "URL list item for displaying all shortened URLs")
public class UrlListItem {

    @Schema(description = "The short key")
    private String shortKey;

    @Schema(description = "The custom alias if provided")
    private String customAlias;

    @Schema(description = "The effective key (custom alias or short key)")
    private String effectiveKey;

    @Schema(description = "The original URL")
    private String originalUrl;

    @Schema(description = "The full short URL")
    private String shortUrl;

    @Schema(description = "Total click count")
    private Long clickCount;

    @Schema(description = "When the URL was created")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "When the URL expires (null if never)")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @Schema(description = "Whether the URL is password protected")
    private Boolean isPasswordProtected;

    @Schema(description = "Page title from metadata")
    private String metaTitle;

    @Schema(description = "AI-determined category")
    private String aiCategory;
}

