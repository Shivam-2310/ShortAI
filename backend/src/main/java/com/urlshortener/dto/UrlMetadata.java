package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Extracted metadata from a URL (Open Graph, Twitter Cards, etc.)")
public class UrlMetadata {

    @Schema(description = "The URL that was analyzed")
    private String url;

    @Schema(description = "Page title", example = "GitHub: Let's build from here")
    private String title;

    @Schema(description = "Page description", example = "GitHub is where millions of developers...")
    private String description;

    @Schema(description = "Open Graph image URL")
    private String imageUrl;

    @Schema(description = "Favicon URL")
    private String faviconUrl;

    @Schema(description = "Site name from og:site_name", example = "GitHub")
    private String siteName;

    @Schema(description = "Content type from og:type", example = "website")
    private String type;

    @Schema(description = "Author from meta tags")
    private String author;

    @Schema(description = "Keywords from meta tags")
    private String keywords;

    @Schema(description = "Canonical URL")
    private String canonicalUrl;

    @Schema(description = "Plain text content (truncated) for AI analysis", hidden = true)
    private String textContent;
}

