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
@Schema(description = "Statistics and analytics for a shortened URL")
public class UrlStatsResponse {

    @Schema(
            description = "The original long URL",
            example = "https://example.com/very/long/path/to/resource"
    )
    private String originalUrl;

    @Schema(
            description = "Total number of times the short URL has been accessed/clicked",
            example = "42"
    )
    private Long clickCount;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(
            description = "When the short URL was created (ISO 8601 format)",
            example = "2025-01-15T10:30:00"
    )
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(
            description = "When the short URL expires (ISO 8601 format). Null if no expiration.",
            example = "2025-12-31T23:59:59",
            nullable = true
    )
    private LocalDateTime expiresAt;
}
