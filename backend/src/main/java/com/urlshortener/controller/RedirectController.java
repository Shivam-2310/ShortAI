package com.urlshortener.controller;

import com.urlshortener.dto.ErrorResponse;
import com.urlshortener.dto.PasswordVerifyRequest;
import com.urlshortener.exception.InvalidPasswordException;
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
    public ResponseEntity<?> redirect(
            @Parameter(description = "The short key or custom alias")
            @PathVariable String shortKey,
            @Parameter(description = "Password for password-protected URLs")
            @RequestParam(required = false) String password,
            HttpServletRequest request) {
        log.debug("Redirect request for key: {}", shortKey);

        try {
            String originalUrl;
            
            // If password is provided, try to resolve with password
            if (password != null && !password.isEmpty()) {
                originalUrl = urlShortenerService.resolveUrlWithPassword(shortKey, password);
            } else {
                originalUrl = urlShortenerService.resolveUrl(shortKey);
            }

            // Extract request data before async call (HttpServletRequest cannot be used in async)
            var requestData = enhancedAnalyticsService.extractRequestData(request);
            // Track detailed analytics asynchronously
            enhancedAnalyticsService.trackClick(shortKey, requestData);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.LOCATION, originalUrl);
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (PasswordRequiredException e) {
            // Return HTML password form page
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(generatePasswordFormHtml(shortKey));
        } catch (InvalidPasswordException e) {
            // Return HTML password form with error message
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(generatePasswordFormHtml(shortKey, "Invalid password. Please try again."));
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

    /**
     * Generates an HTML password form for password-protected URLs.
     */
    private String generatePasswordFormHtml(String shortKey, String errorMessage) {
        String errorHtml = errorMessage != null 
            ? "<div style='color: #dc2626; background: #fee2e2; padding: 12px; border-radius: 6px; margin-bottom: 20px; font-size: 14px;'>" + 
              "<strong>⚠️</strong> " + errorMessage + "</div>" 
            : "";
        
        return "<!DOCTYPE html>" +
               "<html lang='en'>" +
               "<head>" +
               "<meta charset='UTF-8'>" +
               "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
               "<title>Password Required - URL Shortener</title>" +
               "<style>" +
               "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; " +
               "background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); margin: 0; padding: 0; " +
               "min-height: 100vh; display: flex; align-items: center; justify-content: center; }" +
               ".container { background: white; padding: 40px; border-radius: 12px; " +
               "box-shadow: 0 20px 60px rgba(0,0,0,0.3); max-width: 400px; width: 100%; }" +
               "h1 { color: #1f2937; margin: 0 0 10px 0; font-size: 24px; font-weight: 600; }" +
               ".subtitle { color: #6b7280; margin: 0 0 30px 0; font-size: 14px; }" +
               ".form-group { margin-bottom: 20px; }" +
               "label { display: block; margin-bottom: 8px; color: #374151; font-weight: 500; font-size: 14px; }" +
               "input { width: 100%; padding: 12px; border: 2px solid #e5e7eb; border-radius: 8px; " +
               "font-size: 16px; box-sizing: border-box; transition: border-color 0.2s; }" +
               "input:focus { outline: none; border-color: #667eea; }" +
               "button { width: 100%; padding: 14px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); " +
               "color: white; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; " +
               "cursor: pointer; transition: transform 0.1s, box-shadow 0.2s; }" +
               "button:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4); }" +
               "button:active { transform: translateY(0); }" +
               ".lock-icon { text-align: center; font-size: 48px; margin-bottom: 20px; }" +
               "</style>" +
               "</head>" +
               "<body>" +
               "<div class='container'>" +
               "<div class='lock-icon'>🔒</div>" +
               "<h1>Password Required</h1>" +
               "<p class='subtitle'>This link is password protected. Please enter the password to continue.</p>" +
               errorHtml +
               "<form method='GET' action='/" + shortKey + "'>" +
               "<div class='form-group'>" +
               "<label for='password'>Password</label>" +
               "<input type='password' id='password' name='password' placeholder='Enter password' required autofocus>" +
               "</div>" +
               "<button type='submit'>Unlock & Continue</button>" +
               "</form>" +
               "</div>" +
               "<script>" +
               "document.getElementById('password').focus();" +
               "document.querySelector('form').addEventListener('submit', function(e) {" +
               "  const password = document.getElementById('password').value;" +
               "  if (!password) { e.preventDefault(); return; }" +
               "});" +
               "</script>" +
               "</body>" +
               "</html>";
    }

    /**
     * Generates an HTML password form for password-protected URLs (without error message).
     */
    private String generatePasswordFormHtml(String shortKey) {
        return generatePasswordFormHtml(shortKey, null);
    }
}
