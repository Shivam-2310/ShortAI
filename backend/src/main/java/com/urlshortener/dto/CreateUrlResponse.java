package com.urlshortener.dto;

import com.urlshortener.dto.ai.AiAnalysisResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing the generated short URL with metadata")
public class CreateUrlResponse {

    @Schema(description = "The full short URL for redirection", example = "http://localhost:8080/aZ3x9")
    private String shortUrl;

    @Schema(description = "The short key (Base62 encoded)", example = "aZ3x9")
    private String shortKey;

    @Schema(description = "The custom alias if provided", example = "my-cool-link")
    private String customAlias;

    @Schema(description = "Whether password protection is enabled")
    private Boolean isPasswordProtected;

    @Schema(description = "QR code as Base64 PNG (if requested)")
    private String qrCode;

    @Schema(description = "Extracted URL metadata")
    private UrlMetadata metadata;

    @Schema(description = "AI analysis results (if enabled)")
    private AiAnalysisResult aiAnalysis;
}
