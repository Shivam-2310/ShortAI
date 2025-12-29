package com.urlshortener.controller;

import com.urlshortener.dto.*;
import com.urlshortener.dto.analytics.DetailedAnalytics;
import com.urlshortener.service.EnhancedAnalyticsService;
import com.urlshortener.service.QrCodeService;
import com.urlshortener.service.UrlShortenerService;
import com.urlshortener.util.CsvParser;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
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
        try {
            if (request == null) {
                log.warn("Bulk create request is null");
                return ResponseEntity.badRequest()
                        .body(BulkCreateResponse.builder()
                                .successCount(0)
                                .failedCount(0)
                                .results(Collections.emptyList())
                                .errors(Collections.emptyList())
                                .build());
            }
            
            if (request.getUrls() == null || request.getUrls().isEmpty()) {
                log.warn("Bulk create request has no URLs");
                return ResponseEntity.badRequest()
                        .body(BulkCreateResponse.builder()
                                .successCount(0)
                                .failedCount(0)
                                .results(Collections.emptyList())
                                .errors(Collections.emptyList())
                                .build());
            }
            
            log.info("Bulk creating {} short URLs", request.getUrls().size());
            BulkCreateResponse response = urlShortenerService.createBulkShortUrls(request);
            log.info("Bulk creation completed: {} success, {} failed", response.getSuccessCount(), response.getFailedCount());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid bulk create request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(BulkCreateResponse.builder()
                            .successCount(0)
                            .failedCount(0)
                            .results(Collections.emptyList())
                            .errors(Collections.emptyList())
                            .build());
        } catch (Exception e) {
            log.error("Error in bulk create endpoint: {}", e.getMessage(), e);
            // Return a proper error response instead of throwing
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BulkCreateResponse.builder()
                            .successCount(0)
                            .failedCount(0)
                            .results(Collections.emptyList())
                            .errors(Collections.emptyList())
                            .build());
        }
    }

    @Operation(
            summary = "Bulk create short URLs from CSV",
            description = "Upload a CSV file containing URLs (one per line) to create multiple shortened URLs. " +
                    "Maximum 100 URLs per file. CSV format: one URL per line, or CSV with 'url' header."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Bulk creation completed",
                    content = @Content(schema = @Schema(implementation = BulkCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid CSV file or request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/bulk/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BulkCreateResponse> createBulkShortUrlsFromCsv(
            @Parameter(description = "CSV file containing URLs (one per line)")
            @RequestParam(value = "file", required = false) MultipartFile file,
            @Parameter(description = "Whether to fetch metadata for all URLs")
            @RequestParam(value = "fetchMetadata", required = false) String fetchMetadataStr,
            @Parameter(description = "Whether to enable AI analysis for all URLs")
            @RequestParam(value = "enableAiAnalysis", required = false) String enableAiAnalysisStr) {
        
        log.info("CSV upload endpoint called - file: {}, fetchMetadata: {}, enableAiAnalysis: {}", 
                file != null ? file.getOriginalFilename() : "null", fetchMetadataStr, enableAiAnalysisStr);
        
        // Parse boolean parameters safely
        Boolean fetchMetadata = "true".equalsIgnoreCase(fetchMetadataStr);
        Boolean enableAiAnalysis = "true".equalsIgnoreCase(enableAiAnalysisStr);
        
        if (file == null || file.isEmpty()) {
            log.warn("CSV file upload failed: file is null or empty");
            return ResponseEntity.badRequest()
                    .body(BulkCreateResponse.builder()
                            .successCount(0)
                            .failedCount(0)
                            .results(Collections.emptyList())
                            .errors(Collections.emptyList())
                            .build());
        }
        
        log.info("Bulk creating short URLs from CSV file: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
        
        try {
            // Parse CSV file
            List<CreateUrlRequest> urlRequests = CsvParser.parseCsvFile(file);
            
            // Apply bulk-level settings
            for (CreateUrlRequest urlRequest : urlRequests) {
                urlRequest.setFetchMetadata(fetchMetadata);
                urlRequest.setEnableAiAnalysis(enableAiAnalysis);
            }
            
            // Create bulk request
            BulkCreateRequest bulkRequest = BulkCreateRequest.builder()
                    .urls(urlRequests)
                    .fetchMetadata(fetchMetadata)
                    .enableAiAnalysis(enableAiAnalysis)
                    .build();
            
            // Process bulk creation
            BulkCreateResponse response = urlShortenerService.createBulkShortUrls(bulkRequest);
            log.info("Bulk CSV creation completed: {} success, {} failed", response.getSuccessCount(), response.getFailedCount());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid CSV file: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(BulkCreateResponse.builder()
                            .successCount(0)
                            .failedCount(0)
                            .results(Collections.emptyList())
                            .errors(Collections.emptyList())
                            .build());
        } catch (Exception e) {
            log.error("Error processing CSV file: {}", e.getMessage(), e);
            e.printStackTrace(); // Print full stack trace for debugging
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BulkCreateResponse.builder()
                            .successCount(0)
                            .failedCount(0)
                            .results(Collections.emptyList())
                            .errors(Collections.emptyList())
                            .build());
        }
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
