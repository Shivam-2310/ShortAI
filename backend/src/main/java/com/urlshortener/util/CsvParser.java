package com.urlshortener.util;

import com.urlshortener.dto.CreateUrlRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing CSV files containing URLs.
 * Supports simple format: one URL per line, or CSV with header row.
 */
public class CsvParser {

    private static final Logger log = LoggerFactory.getLogger(CsvParser.class);
    private static final int MAX_URLS = 100;
    private static final int MAX_FILE_SIZE = 1024 * 1024; // 1MB

    /**
     * Parse CSV file and extract URLs.
     * Supports formats:
     * - Simple: one URL per line
     * - CSV with header: url,originalUrl (first column is used)
     *
     * @param file the uploaded CSV file
     * @return list of CreateUrlRequest objects
     * @throws IllegalArgumentException if file is invalid or exceeds limits
     */
    public static List<CreateUrlRequest> parseCsvFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 1MB");
        }

        List<CreateUrlRequest> urls = new ArrayList<>();
        boolean hasHeader = false;
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null && urls.size() < MAX_URLS) {
                lineNumber++;
                line = line.trim();

                // Skip empty lines
                if (line.isEmpty()) {
                    continue;
                }

                // Check if first line is a header
                if (lineNumber == 1 && (line.toLowerCase().startsWith("url") || 
                                        line.toLowerCase().startsWith("originalurl") ||
                                        line.toLowerCase().contains(","))) {
                    hasHeader = true;
                    continue;
                }

                // Extract URL from line
                String url = extractUrlFromLine(line);
                if (url != null && !url.isEmpty()) {
                    // Normalize URL - add http:// if no protocol specified
                    url = normalizeUrl(url);
                    if (url != null) {
                        urls.add(CreateUrlRequest.builder()
                                .originalUrl(url)
                                .fetchMetadata(false) // Disable by default for bulk operations
                                .enableAiAnalysis(false) // Disable by default for bulk operations
                                .generateQrCode(false) // Disable by default for bulk operations
                                .build());
                    } else {
                        log.warn("Skipping invalid URL at line {}: {}", lineNumber, line);
                    }
                }
            }

            if (urls.isEmpty()) {
                throw new IllegalArgumentException("No valid URLs found in CSV file");
            }

            log.info("Parsed {} URLs from CSV file ({} lines processed)", urls.size(), lineNumber);
            return urls;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parsing CSV file at line {}: {}", lineNumber, e.getMessage());
            throw new IllegalArgumentException("Failed to parse CSV file: " + e.getMessage());
        }
    }

    /**
     * Extract URL from a CSV line.
     * Handles both simple format (just URL) and CSV format (comma-separated).
     */
    private static String extractUrlFromLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        // If line contains comma, treat as CSV and take first column
        if (line.contains(",")) {
            String[] parts = line.split(",", 2);
            String url = parts[0].trim();
            // Remove quotes if present
            if ((url.startsWith("\"") && url.endsWith("\"")) ||
                (url.startsWith("'") && url.endsWith("'"))) {
                url = url.substring(1, url.length() - 1);
            }
            return url;
        }

        // Otherwise, treat entire line as URL
        return line.trim();
    }

    /**
     * Normalize URL by adding protocol if missing.
     * Returns null if URL cannot be normalized.
     */
    private static String normalizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        url = url.trim();

        // If URL already has a protocol, return as-is
        if (url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return url;
        }

        // If URL starts with //, add http:
        if (url.startsWith("//")) {
            return "http:" + url;
        }

        // If URL looks like a domain (contains dots and no spaces), add https://
        if (url.matches("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}.*") && !url.contains(" ")) {
            return "https://" + url;
        }

        // If URL starts with www., add https://
        if (url.toLowerCase().startsWith("www.")) {
            return "https://" + url;
        }

        // Return null if we can't normalize it
        return null;
    }
}

