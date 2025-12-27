package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for bulk URL shortening")
public class BulkCreateRequest {

    @NotEmpty(message = "URLs list cannot be empty")
    @Size(max = 100, message = "Maximum 100 URLs per batch")
    @Valid
    @Schema(description = "List of URLs to shorten (max 100)")
    private List<CreateUrlRequest> urls;

    @Schema(description = "Whether to fetch metadata for all URLs", example = "false")
    @Builder.Default
    private Boolean fetchMetadata = false;

    @Schema(description = "Whether to enable AI analysis for all URLs", example = "false")
    @Builder.Default
    private Boolean enableAiAnalysis = false;
}

