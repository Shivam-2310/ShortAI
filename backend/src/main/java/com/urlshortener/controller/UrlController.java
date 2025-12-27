package com.urlshortener.controller;

import com.urlshortener.dto.*;
import com.urlshortener.dto.analytics.DetailedAnalytics;
import com.urlshortener.service.EnhancedAnalyticsService;
import com.urlshortener.service.QrCodeService;
import com.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for URL shortening API endpoints.
 */
@RestController
@RequestMapping("/api/urls")
@Tag(name = "URL Shortener", description = "APIs for creating and managing shortened URLs")
public class UrlController {

    private static final Logger log = LoggerFactory.getLogger(UrlController.class);

    private final UrlShortenerService urlShortenerService;
    private final EnhancedAnalyticsService enhancedAnalyticsService;
    private final QrCodeService qrCodeService;

    @Autowired
    public UrlController(
            UrlShortenerService urlShortenerService,
            EnhancedAnalyticsService enhancedAnalyticsService,
            QrCodeService qrCodeService) {
        this.urlShortenerService = urlShortenerService;
        this.enhancedAnalyticsService = enhancedAnalyticsService;
        this.qrCodeService = qrCodeService;
    }

    @Operation(
            summary = "Create a short URL",
            description = "Creates a shortened URL with optional features: custom alias, password protection, " +
                    "metadata extraction, AI analysis, and QR code generation."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Short URL created successfully",
                    content = @Content(schema = @Schema(implementation = CreateUrlResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid URL or request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<CreateUrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        log.info("Creating short URL for: {}", request.getOriginalUrl());
        CreateUrlResponse response = urlShortenerService.createShortUrl(request);
        log.info("Created short URL: {} -> {}", response.getShortKey(), request.getOriginalUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Bulk create short URLs",
            description = "Creates multiple shortened URLs in a single request. Maximum 100 URLs per batch."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Bulk creation completed",
                    content = @Content(schema = @Schema(implementation = BulkCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/bulk")
    public ResponseEntity<BulkCreateResponse> createBulkShortUrls(@Valid @RequestBody BulkCreateRequest request) {
        log.info("Bulk creating {} short URLs", request.getUrls().size());
        BulkCreateResponse response = urlShortenerService.createBulkShortUrls(request);
        log.info("Bulk creation completed: {} success, {} failed", response.getSuccessCount(), response.getFailedCount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get all shortened URLs",
            description = "Retrieves a list of all active shortened URLs, ordered by creation date (most recent first)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of URLs retrieved",
                    content = @Content(schema = @Schema(implementation = UrlListItem.class)))
    })
    @GetMapping
    public ResponseEntity<List<UrlListItem>> getAllUrls() {
        List<UrlListItem> urls = urlShortenerService.getAllUrls();
        return ResponseEntity.ok(urls);
    }

    @Operation(
            summary = "Get basic URL statistics",
            description = "Retrieves basic analytics for a shortened URL including click count."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved",
                    content = @Content(schema = @Schema(implementation = UrlStatsResponse.class))),
            @ApiResponse(responseCode = "404", description = "URL not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{shortKey}/stats")
    public ResponseEntity<UrlStatsResponse> getStats(
            @Parameter(description = "The short key or custom alias") @PathVariable String shortKey) {
        UrlStatsResponse stats = urlShortenerService.getStats(shortKey);
        return ResponseEntity.ok(stats);
    }

    @Operation(
            summary = "Get detailed URL analytics",
            description = "Retrieves comprehensive analytics including geographic distribution, device types, " +
                    "browsers, referrers, and click trends over time."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analytics retrieved",
                    content = @Content(schema = @Schema(implementation = DetailedAnalytics.class))),
            @ApiResponse(responseCode = "404", description = "URL not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{shortKey}/analytics")
    public ResponseEntity<DetailedAnalytics> getDetailedAnalytics(
            @Parameter(description = "The short key or custom alias") @PathVariable String shortKey) {
        DetailedAnalytics analytics = enhancedAnalyticsService.getDetailedAnalytics(shortKey);
        if (analytics == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(analytics);
    }

    @Operation(
            summary = "Generate QR code for a short URL",
            description = "Generates a QR code image for the short URL. Supports custom size and colors."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "QR code generated"),
            @ApiResponse(responseCode = "404", description = "URL not found")
    })
    @GetMapping("/{shortKey}/qrcode")
    public ResponseEntity<byte[]> getQrCode(
            @Parameter(description = "The short key or custom alias") @PathVariable String shortKey,
            @Parameter(description = "QR code size in pixels (100-1000)") @RequestParam(defaultValue = "300") int size,
            @Parameter(description = "Foreground color in hex") @RequestParam(defaultValue = "#000000") String fgColor,
            @Parameter(description = "Background color in hex") @RequestParam(defaultValue = "#FFFFFF") String bgColor) {

        byte[] qrCodeBytes = qrCodeService.generateQrCodeBytes(shortKey, size, fgColor, bgColor);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentDisposition(ContentDisposition.inline().filename(shortKey + "-qr.png").build());

        return new ResponseEntity<>(qrCodeBytes, headers, HttpStatus.OK);
    }

    @Operation(
            summary = "Get URL preview",
            description = "Gets preview information for a URL (useful for password-protected links)."
    )
    @GetMapping("/{shortKey}/preview")
    public ResponseEntity<UrlMetadata> getUrlPreview(
            @Parameter(description = "The short key or custom alias") @PathVariable String shortKey) {
        UrlMetadata preview = urlShortenerService.getUrlPreview(shortKey);
        return ResponseEntity.ok(preview);
    }

    @Operation(
            summary = "Check if URL is password protected",
            description = "Returns whether a short URL requires a password to access."
    )
    @GetMapping("/{shortKey}/protected")
    public ResponseEntity<ProtectionStatus> checkProtection(
            @Parameter(description = "The short key or custom alias") @PathVariable String shortKey) {
        boolean isProtected = urlShortenerService.isPasswordProtected(shortKey);
        return ResponseEntity.ok(new ProtectionStatus(isProtected));
    }

    @Schema(description = "URL protection status")
    public record ProtectionStatus(
            @Schema(description = "Whether password is required") boolean passwordRequired) {}
}
