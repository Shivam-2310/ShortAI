package com.urlshortener.controller;

import com.urlshortener.dto.ErrorResponse;
import com.urlshortener.dto.PasswordVerifyRequest;
import com.urlshortener.exception.PasswordRequiredException;
import com.urlshortener.service.EnhancedAnalyticsService;
import com.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for URL redirect endpoints.
 * Optimized for low-latency cache-first redirects.
 */
@RestController
@Tag(name = "Redirect", description = "URL redirect endpoints")
public class RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    private final UrlShortenerService urlShortenerService;
    private final EnhancedAnalyticsService enhancedAnalyticsService;

    @Autowired
    public RedirectController(
            UrlShortenerService urlShortenerService,
            EnhancedAnalyticsService enhancedAnalyticsService) {
        this.urlShortenerService = urlShortenerService;
        this.enhancedAnalyticsService = enhancedAnalyticsService;
    }

    @Operation(
            summary = "Redirect to original URL",
            description = "Redirects to the original URL. Uses cache-first strategy for performance. " +
                    "Rate limited to 100 requests/minute/IP. Returns 401 if password protected."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "302", description = "Redirect to original URL",
                    headers = @Header(name = "Location", description = "The original URL")),
            @ApiResponse(responseCode = "401", description = "Password required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "URL not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "URL expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{shortKey:[a-zA-Z0-9_-]{1,50}}")
    public ResponseEntity<Void> redirect(
            @Parameter(description = "The short key or custom alias")
            @PathVariable String shortKey,
            HttpServletRequest request) {
        log.debug("Redirect request for key: {}", shortKey);

        try {
            String originalUrl = urlShortenerService.resolveUrl(shortKey);

            // Extract request data before async call (HttpServletRequest cannot be used in async)
            var requestData = enhancedAnalyticsService.extractRequestData(request);
            // Track detailed analytics asynchronously
            enhancedAnalyticsService.trackClick(shortKey, requestData);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.LOCATION, originalUrl);
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (PasswordRequiredException e) {
            // Return 401 to indicate password is required
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Password-Required", "true");
            return new ResponseEntity<>(headers, HttpStatus.UNAUTHORIZED);
        }
    }

    @Operation(
            summary = "Redirect with password",
            description = "Redirects to a password-protected URL after verifying the password."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "302", description = "Redirect to original URL",
                    headers = @Header(name = "Location", description = "The original URL")),
            @ApiResponse(responseCode = "401", description = "Invalid password",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "URL not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{shortKey:[a-zA-Z0-9_-]{1,50}}/unlock")
    public ResponseEntity<Void> redirectWithPassword(
            @Parameter(description = "The short key or custom alias")
            @PathVariable String shortKey,
            @Valid @RequestBody PasswordVerifyRequest passwordRequest,
            HttpServletRequest request) {
        log.debug("Password-protected redirect for key: {}", shortKey);

        String originalUrl = urlShortenerService.resolveUrlWithPassword(shortKey, passwordRequest.getPassword());

        // Extract request data before async call
        var requestData = enhancedAnalyticsService.extractRequestData(request);
        // Track analytics asynchronously
        enhancedAnalyticsService.trackClick(shortKey, requestData);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, originalUrl);
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
