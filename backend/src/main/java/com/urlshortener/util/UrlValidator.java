package com.urlshortener.util;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * URL validation utility to prevent open redirects and validate URL format.
 */
@Component
public class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int MAX_URL_LENGTH = 2048;

    /**
     * Validates if the URL is properly formatted and safe.
     *
     * @param url the URL to validate
     * @return true if valid, false otherwise
     */
    public boolean isValid(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        if (url.length() > MAX_URL_LENGTH) {
            return false;
        }

        try {
            URI uri = new URI(url);

            // Check scheme
            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
                return false;
            }

            // Check host exists
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }

            // Prevent javascript: and data: URLs (already handled by scheme check)
            // Prevent URLs with credentials
            if (uri.getUserInfo() != null) {
                return false;
            }

            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Sanitizes the URL by trimming whitespace.
     *
     * @param url the URL to sanitize
     * @return sanitized URL
     */
    public String sanitize(String url) {
        if (url == null) {
            return null;
        }
        return url.trim();
    }
}

