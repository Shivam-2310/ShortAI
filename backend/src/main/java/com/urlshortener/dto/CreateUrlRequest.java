package com.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for creating a short URL")
public class CreateUrlRequest {

    @NotBlank(message = "Original URL is required")
    @URL(message = "Invalid URL format")
    @Schema(
            description = "The original long URL to shorten",
            example = "https://example.com/very/long/path/to/resource",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String originalUrl;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(
            description = "Optional expiration date/time for the short URL (ISO 8601 format)",
            example = "2025-12-31T23:59:59"
    )
    private LocalDateTime expiresAt;

    @Size(min = 3, max = 50, message = "Custom alias must be 3-50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9-_]+$", message = "Custom alias can only contain letters, numbers, hyphens, and underscores")
    @Schema(
            description = "Optional custom alias for the short URL (3-50 chars, alphanumeric with hyphens/underscores)",
            example = "my-cool-link"
    )
    private String customAlias;

    @Size(min = 4, max = 128, message = "Password must be 4-128 characters")
    @Schema(
            description = "Optional password to protect the URL",
            example = "secret123"
    )
    private String password;

    @Schema(
            description = "Whether to fetch URL metadata (title, description, image)",
            example = "true"
    )
    @Builder.Default
    private Boolean fetchMetadata = true;

    @Schema(
            description = "Whether to perform AI analysis (summary, category, safety check)",
            example = "true"
    )
    @Builder.Default
    private Boolean enableAiAnalysis = true;

    @Schema(
            description = "Whether to generate a QR code for the short URL",
            example = "true"
    )
    @Builder.Default
    private Boolean generateQrCode = false;
}
