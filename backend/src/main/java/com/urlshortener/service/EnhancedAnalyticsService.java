package com.urlshortener.service;

import com.urlshortener.dto.analytics.DetailedAnalytics;
import com.urlshortener.entity.ClickAnalytics;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.repository.ClickAnalyticsRepository;
import com.urlshortener.repository.UrlMappingRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import ua_parser.Client;
import ua_parser.Parser;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enhanced analytics service with detailed tracking.
 * Tracks: device type, browser, OS, geolocation, referrer.
 */
@Service
public class EnhancedAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(EnhancedAnalyticsService.class);

    private final ClickAnalyticsRepository clickAnalyticsRepository;
    private final UrlMappingRepository urlMappingRepository;
    private final WebClient geoIpClient;
    private final Parser uaParser;

    @Autowired
    public EnhancedAnalyticsService(
            ClickAnalyticsRepository clickAnalyticsRepository,
            UrlMappingRepository urlMappingRepository,
            WebClient.Builder webClientBuilder) {
        this.clickAnalyticsRepository = clickAnalyticsRepository;
        this.urlMappingRepository = urlMappingRepository;
        this.geoIpClient = webClientBuilder.baseUrl("http://ip-api.com").build();
        this.uaParser = new Parser();
    }

    /**
     * Extract request info synchronously before async processing.
     */
    public ClickTrackingData extractRequestData(HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");
        return new ClickTrackingData(clientIp, userAgent, referer);
    }

    /**
     * Track a click asynchronously with full analytics.
     */
    @Async("analyticsExecutor")
    @Transactional
    public void trackClick(String shortKey, ClickTrackingData requestData) {
        try {
            // Support both short key and custom alias
            Optional<UrlMapping> urlMappingOpt = urlMappingRepository.findByShortKeyOrCustomAlias(shortKey, shortKey);
            if (urlMappingOpt.isEmpty()) {
                log.warn("Cannot track click - URL mapping not found for key: {}", shortKey);
                return;
            }

            UrlMapping urlMapping = urlMappingOpt.get();
            String actualShortKey = urlMapping.getShortKey(); // Use actual short key for increment

            // Increment click count using actual short key
            urlMappingRepository.incrementClickCount(actualShortKey);

            // Use extracted request info
            String clientIp = requestData.clientIp;
            String userAgent = requestData.userAgent;
            String referer = requestData.referer;

            // Parse user agent
            Client client = parseUserAgent(userAgent);

            // Build analytics record
            ClickAnalytics.ClickAnalyticsBuilder builder = ClickAnalytics.builder()
                    .urlMapping(urlMapping)
                    .clickedAt(LocalDateTime.now())
                    .clientIp(clientIp)
                    .userAgent(userAgent)
                    .referer(referer);

            // Always set device type - use improved detection
            String deviceType = determineDeviceType(client, userAgent);
            builder.deviceType(deviceType);

            // Add parsed UA info if available
            if (client != null) {
                if (client.userAgent != null && client.userAgent.family != null) {
                    builder.browserName(client.userAgent.family);
                    if (client.userAgent.major != null) {
                        builder.browserVersion(client.userAgent.major);
                    }
                }
                if (client.os != null && client.os.family != null) {
                    builder.osName(client.os.family);
                    if (client.os.major != null) {
                        builder.osVersion(client.os.major);
                    }
                }
            } else {
                // Fallback: try to extract basic info from user agent string
                if (userAgent != null && !userAgent.isEmpty()) {
                    String ua = userAgent.toLowerCase();
                    // Try to extract browser name
                    if (ua.contains("chrome") && !ua.contains("edg")) {
                        builder.browserName("Chrome");
                    } else if (ua.contains("firefox")) {
                        builder.browserName("Firefox");
                    } else if (ua.contains("safari") && !ua.contains("chrome")) {
                        builder.browserName("Safari");
                    } else if (ua.contains("edg")) {
                        builder.browserName("Edge");
                    } else if (ua.contains("opera")) {
                        builder.browserName("Opera");
                    }
                    
                    // Try to extract OS
                    if (ua.contains("windows")) {
                        builder.osName("Windows");
                    } else if (ua.contains("mac")) {
                        builder.osName("macOS");
                    } else if (ua.contains("linux")) {
                        builder.osName("Linux");
                    } else if (ua.contains("android")) {
                        builder.osName("Android");
                    } else if (ua.contains("ios") || ua.contains("iphone") || ua.contains("ipad")) {
                        builder.osName("iOS");
                    }
                }
            }

            // Fetch geolocation (async within async)
            GeoInfo geoInfo = fetchGeoLocation(clientIp);
            if (geoInfo != null && geoInfo.countryCode != null) {
                builder.countryCode(geoInfo.countryCode)
                        .countryName(geoInfo.country != null ? geoInfo.country : "Unknown")
                        .city(geoInfo.city != null ? geoInfo.city : null)
                        .region(geoInfo.regionName != null ? geoInfo.regionName : null)
                        .timezone(geoInfo.timezone != null ? geoInfo.timezone : null);
            } else {
                log.debug("No geolocation data available for IP: {}", clientIp);
            }

            ClickAnalytics analytics = builder.build();
            clickAnalyticsRepository.save(analytics);
            log.info("Tracked detailed click for key: {} - Device: {}, Browser: {}, OS: {}, Country: {}", 
                    shortKey, 
                    analytics.getDeviceType(), 
                    analytics.getBrowserName(), 
                    analytics.getOsName(), 
                    analytics.getCountryCode());

        } catch (Exception e) {
            log.error("Error tracking click for key: {}", shortKey, e);
        }
    }

    /**
     * Get detailed analytics for a URL.
     */
    @Transactional(readOnly = true)
    public DetailedAnalytics getDetailedAnalytics(String shortKey) {
        // Support both short key and custom alias
        Optional<UrlMapping> urlMappingOpt = urlMappingRepository.findByShortKeyOrCustomAlias(shortKey, shortKey);
        if (urlMappingOpt.isEmpty()) {
            return null;
        }

        UrlMapping urlMapping = urlMappingOpt.get();
        Long urlId = urlMapping.getId();

        // Use effective key (custom alias takes priority)
        String effectiveKey = urlMapping.getEffectiveKey();

        // Build analytics
        DetailedAnalytics.DetailedAnalyticsBuilder builder = DetailedAnalytics.builder()
                .shortKey(effectiveKey)
                .originalUrl(urlMapping.getOriginalUrl())
                .totalClicks(urlMapping.getClickCount())
                .createdAt(urlMapping.getCreatedAt())
                .expiresAt(urlMapping.getExpiresAt());

        // Get breakdowns - ensure we handle empty results
        List<Object[]> countryData = clickAnalyticsRepository.countByCountry(urlId);
        List<Object[]> deviceData = clickAnalyticsRepository.countByDeviceType(urlId);
        List<Object[]> browserData = clickAnalyticsRepository.countByBrowser(urlId);
        List<Object[]> osData = clickAnalyticsRepository.countByOs(urlId);
        List<Object[]> refererData = clickAnalyticsRepository.countByReferer(urlId);
        
        log.debug("Analytics data for URL {}: Countries={}, Devices={}, Browsers={}, OS={}, Referers={}", 
                urlId, countryData.size(), deviceData.size(), browserData.size(), osData.size(), refererData.size());
        
        builder.clicksByCountry(toMap(countryData));
        builder.clicksByDevice(toMap(deviceData));
        builder.clicksByBrowser(toMap(browserData));
        builder.clicksByOs(toMap(osData));
        builder.clicksByReferer(toMap(refererData));

        // Get clicks over time (last 30 days)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Object[]> timeData = clickAnalyticsRepository.countByDay(urlId, thirtyDaysAgo);
        log.debug("Time series data for URL {}: {} days", urlId, timeData.size());
        builder.clicksOverTime(toMap(timeData));

        // AI info
        builder.aiSummary(urlMapping.getAiSummary());
        builder.aiCategory(urlMapping.getAiCategory());
        builder.aiTags(urlMapping.getAiTags());

        // Metadata
        builder.metaTitle(urlMapping.getMetaTitle());
        builder.metaDescription(urlMapping.getMetaDescription());
        builder.metaImageUrl(urlMapping.getMetaImageUrl());

        return builder.build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private Client parseUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return null;
        }
        try {
            return uaParser.parse(userAgent);
        } catch (Exception e) {
            log.debug("Failed to parse user agent: {}", userAgent);
            return null;
        }
    }

    /**
     * Improved device type detection using ua-parser device family and user agent analysis.
     * Uses multiple strategies for accurate device classification.
     */
    private String determineDeviceType(Client client, String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }

        String ua = userAgent.toLowerCase();

        // Strategy 1: Use ua-parser device family if available
        if (client != null && client.device != null && client.device.family != null) {
            String deviceFamily = client.device.family.toLowerCase();
            
            // ua-parser device family classifications
            if (deviceFamily.contains("iphone") || deviceFamily.contains("ipod")) {
                return "Mobile";
            }
            if (deviceFamily.contains("ipad")) {
                return "Tablet";
            }
            if (deviceFamily.contains("android")) {
                // Android can be mobile or tablet - check for tablet indicators
                if (ua.contains("tablet") || ua.contains("pad") || 
                    ua.contains("gt-") || ua.contains("sm-t") || 
                    ua.contains("nexus 7") || ua.contains("nexus 9") ||
                    ua.contains("nexus 10")) {
                    return "Tablet";
                }
                // Check screen size indicators in user agent
                if (ua.matches(".*\\b(7|8|9|10|11|12)\\s*(inch|in)\\b.*")) {
                    return "Tablet";
                }
                return "Mobile";
            }
            if (deviceFamily.contains("blackberry") || deviceFamily.contains("windows phone")) {
                return "Mobile";
            }
            if (deviceFamily.contains("kindle") || deviceFamily.contains("playbook")) {
                return "Tablet";
            }
        }

        // Strategy 2: Detect bots and crawlers
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider") ||
            ua.contains("scraper") || ua.contains("crawling") || ua.contains("headless") ||
            ua.contains("phantom") || ua.contains("selenium") || ua.contains("webdriver")) {
            return "Bot";
        }

        // Strategy 3: Mobile device patterns
        if (ua.contains("mobile") || ua.contains("android") || 
            ua.contains("iphone") || ua.contains("ipod") ||
            ua.contains("blackberry") || ua.contains("windows phone") ||
            ua.contains("opera mini") || ua.contains("opera mobi") ||
            ua.contains("iemobile") || ua.contains("wpdesktop")) {
            // Additional check for tablets
            if (ua.contains("ipad") || ua.contains("tablet") || ua.contains("playbook") ||
                ua.contains("kindle") || ua.contains("xoom") || ua.contains("galaxy tab")) {
                return "Tablet";
            }
            return "Mobile";
        }

        // Strategy 4: Tablet-specific patterns
        if (ua.contains("tablet") || ua.contains("ipad") || ua.contains("playbook") ||
            ua.contains("kindle") || ua.contains("xoom") || ua.contains("galaxy tab") ||
            ua.contains("nexus 7") || ua.contains("nexus 9") || ua.contains("nexus 10") ||
            ua.contains("surface") || ua.contains("touchpad")) {
            return "Tablet";
        }

        // Strategy 5: Desktop patterns
        if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("linux") ||
            ua.contains("x11") || ua.contains("unix") || ua.contains("bsd") ||
            ua.contains("chrome") || ua.contains("firefox") || ua.contains("safari") ||
            ua.contains("edge") || ua.contains("opera")) {
            return "Desktop";
        }

        // Default to Desktop for unknown cases
        return "Desktop";
    }

    /**
     * Enhanced geolocation fetching with better IP validation and error handling.
     * Uses ip-api.com free tier with proper rate limiting considerations.
     */
    private GeoInfo fetchGeoLocation(String ip) {
        // Validate and skip private/local IPs
        if (ip == null || ip.isEmpty()) {
            return null;
        }

        // Normalize IPv6 localhost
        String normalizedIp = ip.trim();
        if (normalizedIp.equals("127.0.0.1") || 
            normalizedIp.equals("0:0:0:0:0:0:0:1") || 
            normalizedIp.equals("::1") ||
            normalizedIp.equals("localhost")) {
            return null;
        }

        // Skip private IP ranges (RFC 1918)
        if (normalizedIp.startsWith("192.168.") || 
            normalizedIp.startsWith("10.") ||
            normalizedIp.startsWith("172.16.") || normalizedIp.startsWith("172.17.") ||
            normalizedIp.startsWith("172.18.") || normalizedIp.startsWith("172.19.") ||
            normalizedIp.startsWith("172.20.") || normalizedIp.startsWith("172.21.") ||
            normalizedIp.startsWith("172.22.") || normalizedIp.startsWith("172.23.") ||
            normalizedIp.startsWith("172.24.") || normalizedIp.startsWith("172.25.") ||
            normalizedIp.startsWith("172.26.") || normalizedIp.startsWith("172.27.") ||
            normalizedIp.startsWith("172.28.") || normalizedIp.startsWith("172.29.") ||
            normalizedIp.startsWith("172.30.") || normalizedIp.startsWith("172.31.") ||
            normalizedIp.startsWith("169.254.") || // Link-local
            normalizedIp.startsWith("fc00:") || normalizedIp.startsWith("fd00:") || // IPv6 private
            normalizedIp.startsWith("fe80:")) { // IPv6 link-local
            log.debug("Skipping geolocation for private IP: {}", normalizedIp);
            return null;
        }

        try {
            // Use ip-api.com free tier (45 requests/minute limit)
            // Format: http://ip-api.com/json/{ip}?fields=status,message,country,countryCode,region,regionName,city,timezone,query
            GeoInfo geoInfo = geoIpClient.get()
                    .uri("/json/{ip}?fields=status,message,country,countryCode,region,regionName,city,timezone,query", normalizedIp)
                    .retrieve()
                    .bodyToMono(GeoInfo.class)
                    .timeout(java.time.Duration.ofSeconds(5)) // Reduced timeout for faster failure
                    .doOnError(error -> log.debug("Geolocation API error for IP {}: {}", normalizedIp, error.getMessage()))
                    .onErrorReturn(null) // Return null on error instead of throwing
                    .block();
            
            // Check if API returned success status
            if (geoInfo != null && "success".equalsIgnoreCase(geoInfo.status)) {
                return geoInfo;
            } else if (geoInfo != null) {
                log.debug("Geolocation API returned failure status for IP {}: {}", normalizedIp, geoInfo.status);
            }
            return null;
        } catch (Exception e) {
            log.debug("Failed to fetch geolocation for IP: {} - {}", normalizedIp, e.getMessage());
            return null;
        }
    }

    private Map<String, Long> toMap(List<Object[]> results) {
        Map<String, Long> map = new HashMap<>();
        if (results == null || results.isEmpty()) {
            return map;
        }
        for (Object[] row : results) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                try {
                    String key = row[0].toString().trim();
                    if (!key.isEmpty() && !key.equals("null") && !key.equals("Unknown")) {
                        Number count = (Number) row[1];
                        long countValue = count != null ? count.longValue() : 0L;
                        if (countValue > 0) {
                            map.put(key, countValue);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error converting analytics row to map: {}", e.getMessage());
                }
            }
        }
        return map;
    }

    // Inner class for geo response
    private static class GeoInfo {
        public String status;
        public String country;
        public String countryCode;
        public String region;
        public String regionName;
        public String city;
        public String zip;
        public Double lat;
        public Double lon;
        public String timezone;
        public String isp;
    }

    // Data class for click tracking data (extracted before async call)
    public static class ClickTrackingData {
        public final String clientIp;
        public final String userAgent;
        public final String referer;

        public ClickTrackingData(String clientIp, String userAgent, String referer) {
            this.clientIp = clientIp;
            this.userAgent = userAgent;
            this.referer = referer;
        }
    }
}

