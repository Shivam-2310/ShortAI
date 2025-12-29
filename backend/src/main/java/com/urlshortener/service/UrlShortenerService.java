package com.urlshortener.service;

import com.urlshortener.dto.*;
import com.urlshortener.dto.ai.AiAnalysisResult;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.exception.*;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.util.Base62Encoder;
import com.urlshortener.util.RandomKeyGenerator;
import com.urlshortener.util.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core service for URL shortening operations.
 * Features:
 * - Cache-first lookup strategy
 * - Custom aliases
 * - Password protection
 * - AI analysis integration
 * - Metadata extraction
 * - QR code generation
 */
@Service
public class UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);

    private final UrlMappingRepository urlMappingRepository;
    private final Base62Encoder base62Encoder;
    private final RandomKeyGenerator randomKeyGenerator;
    private final UrlValidator urlValidator;
    private final CacheService cacheService;
    private final AnalyticsService analyticsService;
    private final OllamaService ollamaService;
    private final UrlMetadataService urlMetadataService;
    private final QrCodeService qrCodeService;
    private final PasswordService passwordService;
    private final String baseUrl;

    @Autowired
    public UrlShortenerService(
            UrlMappingRepository urlMappingRepository,
            Base62Encoder base62Encoder,
            RandomKeyGenerator randomKeyGenerator,
            UrlValidator urlValidator,
            CacheService cacheService,
            AnalyticsService analyticsService,
            OllamaService ollamaService,
            UrlMetadataService urlMetadataService,
            QrCodeService qrCodeService,
            PasswordService passwordService,
            @Value("${app.base-url}") String baseUrl) {
        this.urlMappingRepository = urlMappingRepository;
        this.randomKeyGenerator = randomKeyGenerator;
        this.base62Encoder = base62Encoder;
        this.urlValidator = urlValidator;
        this.cacheService = cacheService;
        this.analyticsService = analyticsService;
        this.ollamaService = ollamaService;
        this.urlMetadataService = urlMetadataService;
        this.qrCodeService = qrCodeService;
        this.passwordService = passwordService;
        this.baseUrl = baseUrl;
    }

    /**
     * Creates a shortened URL with optional features.
     */
    @Transactional
    public CreateUrlResponse createShortUrl(CreateUrlRequest request) {
        String sanitizedUrl = urlValidator.sanitize(request.getOriginalUrl());

        if (!urlValidator.isValid(sanitizedUrl)) {
            throw new InvalidUrlException("Invalid URL format: " + request.getOriginalUrl());
        }

        // Validate custom alias if provided
        if (request.getCustomAlias() != null && !request.getCustomAlias().isEmpty()) {
            if (urlMappingRepository.existsByCustomAlias(request.getCustomAlias())) {
                throw new InvalidUrlException("Custom alias already exists: " + request.getCustomAlias());
            }
        }

        // Build URL mapping
        UrlMapping.UrlMappingBuilder builder = UrlMapping.builder()
                .originalUrl(sanitizedUrl)
                .expiresAt(request.getExpiresAt())
                .customAlias(request.getCustomAlias());

        // Handle password protection
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            if (!passwordService.isValidPassword(request.getPassword())) {
                throw new InvalidUrlException("Password must be 4-128 characters");
            }
            builder.passwordHash(passwordService.hashPassword(request.getPassword()));
            builder.isPasswordProtected(true);
        }

        // Save initial record
        UrlMapping savedMapping = urlMappingRepository.save(builder.build());
        log.debug("Created URL mapping with ID: {}", savedMapping.getId());

        // Generate random short key (6-8 characters) with collision handling
        String shortKey = generateUniqueShortKey();
        savedMapping.setShortKey(shortKey);

        // Fetch metadata if enabled
        UrlMetadata metadata = null;
        if (Boolean.TRUE.equals(request.getFetchMetadata())) {
            try {
                metadata = urlMetadataService.fetchMetadata(sanitizedUrl);
                if (metadata != null) {
                    savedMapping.setMetaTitle(metadata.getTitle());
                    savedMapping.setMetaDescription(metadata.getDescription());
                    savedMapping.setMetaImageUrl(metadata.getImageUrl());
                    savedMapping.setMetaFaviconUrl(metadata.getFaviconUrl());
                    savedMapping.setMetaFetchedAt(LocalDateTime.now());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch metadata for URL: {}", sanitizedUrl, e);
            }
        }

        // AI analysis if enabled - try sync first for immediate response, then async for updates
        AiAnalysisResult aiAnalysis = null;
        if (Boolean.TRUE.equals(request.getEnableAiAnalysis())) {
            try {
                String title = metadata != null ? metadata.getTitle() : null;
                String description = metadata != null ? metadata.getDescription() : null;
                
                // Try to get from cache first (fast)
                aiAnalysis = ollamaService.analyzeUrl(sanitizedUrl, title, description);

                if (aiAnalysis != null && !aiAnalysis.getFromCache()) {
                    // If not from cache, update immediately
                    savedMapping.setAiSummary(aiAnalysis.getSummary());
                    savedMapping.setAiCategory(aiAnalysis.getCategory());
                    savedMapping.setAiTags(aiAnalysis.getTags() != null ?
                            String.join(",", aiAnalysis.getTags()) : null);
                    savedMapping.setAiSafetyScore(aiAnalysis.getSafetyScore());
                    savedMapping.setAiAnalyzedAt(LocalDateTime.now());
                } else if (aiAnalysis != null) {
                    // From cache, update immediately
                    savedMapping.setAiSummary(aiAnalysis.getSummary());
                    savedMapping.setAiCategory(aiAnalysis.getCategory());
                    savedMapping.setAiTags(aiAnalysis.getTags() != null ?
                            String.join(",", aiAnalysis.getTags()) : null);
                    savedMapping.setAiSafetyScore(aiAnalysis.getSafetyScore());
                    savedMapping.setAiAnalyzedAt(LocalDateTime.now());
                }
                
                // Also trigger async analysis for background refresh/update
                ollamaService.analyzeUrlAsync(savedMapping.getId(), sanitizedUrl, title, description);
            } catch (Exception e) {
                log.warn("Failed to perform AI analysis for URL: {}", sanitizedUrl, e);
                // Trigger async anyway - might work in background
                try {
                    ollamaService.analyzeUrlAsync(savedMapping.getId(), sanitizedUrl,
                            metadata != null ? metadata.getTitle() : null,
                            metadata != null ? metadata.getDescription() : null);
                } catch (Exception asyncEx) {
                    log.debug("Async AI analysis also failed: {}", asyncEx.getMessage());
                }
            }
        }

        // Save with all updates
        urlMappingRepository.save(savedMapping);

        // Cache the mapping (only if not password protected)
        if (!Boolean.TRUE.equals(savedMapping.getIsPasswordProtected())) {
            cacheService.put(shortKey, sanitizedUrl);
        }

        // Generate QR code if requested
        String qrCode = null;
        if (Boolean.TRUE.equals(request.getGenerateQrCode())) {
            String effectiveKey = savedMapping.getCustomAlias() != null ?
                    savedMapping.getCustomAlias() : shortKey;
            qrCode = qrCodeService.generateQrCode(effectiveKey);
        }

        // Build response
        String effectiveKey = savedMapping.getCustomAlias() != null ?
                savedMapping.getCustomAlias() : shortKey;
        String shortUrl = baseUrl + "/" + effectiveKey;

        return CreateUrlResponse.builder()
                .shortUrl(shortUrl)
                .shortKey(shortKey)
                .customAlias(savedMapping.getCustomAlias())
                .isPasswordProtected(savedMapping.getIsPasswordProtected())
                .qrCode(qrCode)
                .metadata(metadata)
                .aiAnalysis(aiAnalysis)
                .build();
    }

    /**
     * Creates multiple short URLs in bulk.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkCreateResponse createBulkShortUrls(BulkCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("BulkCreateRequest cannot be null");
        }
        
        if (request.getUrls() == null || request.getUrls().isEmpty()) {
            log.warn("Bulk create request has no URLs");
            return BulkCreateResponse.builder()
                    .successCount(0)
                    .failedCount(0)
                    .results(new ArrayList<>())
                    .errors(new ArrayList<>())
                    .build();
        }

        List<CreateUrlResponse> results = new ArrayList<>();
        List<BulkCreateResponse.BulkError> errors = new ArrayList<>();

        for (int i = 0; i < request.getUrls().size(); i++) {
            CreateUrlRequest urlRequest = request.getUrls().get(i);
            
            if (urlRequest == null) {
                log.warn("Skipping null URL request at index {}", i);
                errors.add(BulkCreateResponse.BulkError.builder()
                        .index(i)
                        .originalUrl("null")
                        .error("URL request is null")
                        .build());
                continue;
            }

            // Validate that originalUrl is not null or empty
            if (urlRequest.getOriginalUrl() == null || urlRequest.getOriginalUrl().trim().isEmpty()) {
                log.warn("Skipping URL request with empty originalUrl at index {}", i);
                errors.add(BulkCreateResponse.BulkError.builder()
                        .index(i)
                        .originalUrl("")
                        .error("Original URL is required")
                        .build());
                continue;
            }

            // Apply bulk-level settings
            if (request.getFetchMetadata() != null) {
                urlRequest.setFetchMetadata(request.getFetchMetadata());
            }
            if (request.getEnableAiAnalysis() != null) {
                urlRequest.setEnableAiAnalysis(request.getEnableAiAnalysis());
            }

            try {
                CreateUrlResponse response = createShortUrl(urlRequest);
                results.add(response);
            } catch (Exception e) {
                log.warn("Failed to create short URL for index {}: {}", i, e.getMessage(), e);
                String originalUrl = urlRequest.getOriginalUrl() != null ? urlRequest.getOriginalUrl() : "unknown";
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                // Truncate error message if too long
                if (errorMessage.length() > 200) {
                    errorMessage = errorMessage.substring(0, 197) + "...";
                }
                errors.add(BulkCreateResponse.BulkError.builder()
                        .index(i)
                        .originalUrl(originalUrl)
                        .error(errorMessage)
                        .build());
            }
        }

        return BulkCreateResponse.builder()
                .successCount(results.size())
                .failedCount(errors.size())
                .results(results)
                .errors(errors)
                .build();
    }

    /**
     * Resolves a short key to original URL using cache-first strategy.
     */
    public String resolveUrl(String key) {
        // Try to find by custom alias or short key
        UrlMapping urlMapping = urlMappingRepository.findByShortKeyOrCustomAlias(key, key)
                .orElseThrow(() -> new UrlNotFoundException(key));

        // Check if password protected
        if (Boolean.TRUE.equals(urlMapping.getIsPasswordProtected())) {
            throw new PasswordRequiredException(key);
        }

        return resolveUrlInternal(urlMapping);
    }

    /**
     * Resolves a password-protected URL after verification.
     */
    public String resolveUrlWithPassword(String key, String password) {
        UrlMapping urlMapping = urlMappingRepository.findByShortKeyOrCustomAlias(key, key)
                .orElseThrow(() -> new UrlNotFoundException(key));

        if (!Boolean.TRUE.equals(urlMapping.getIsPasswordProtected())) {
            return resolveUrlInternal(urlMapping);
        }

        if (!passwordService.verifyPassword(password, urlMapping.getPasswordHash())) {
            throw new InvalidPasswordException(key);
        }

        return resolveUrlInternal(urlMapping);
    }

    private String resolveUrlInternal(UrlMapping urlMapping) {
        // Check if active
        if (!Boolean.TRUE.equals(urlMapping.getIsActive())) {
            throw new UrlInactiveException(urlMapping.getShortKey());
        }

        // Check if expired
        if (urlMapping.isExpired()) {
            cacheService.invalidate(urlMapping.getShortKey());
            throw new UrlExpiredException(urlMapping.getShortKey());
        }

        String originalUrl = urlMapping.getOriginalUrl();
        String shortKey = urlMapping.getShortKey();

        // Try cache first for non-password protected URLs
        if (!Boolean.TRUE.equals(urlMapping.getIsPasswordProtected())) {
            Optional<String> cachedUrl = cacheService.get(shortKey);
            if (cachedUrl.isPresent()) {
                // Click counting is handled by EnhancedAnalyticsService.trackClick() in the controller
                return cachedUrl.get();
            }
            // Populate cache
            cacheService.put(shortKey, originalUrl);
        }

        // Click counting is handled by EnhancedAnalyticsService.trackClick() in the controller
        return originalUrl;
    }

    /**
     * Gets statistics for a URL.
     */
    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String shortKey) {
        UrlMapping urlMapping = urlMappingRepository.findByShortKeyOrCustomAlias(shortKey, shortKey)
                .orElseThrow(() -> new UrlNotFoundException(shortKey));

        return UrlStatsResponse.builder()
                .originalUrl(urlMapping.getOriginalUrl())
                .clickCount(urlMapping.getClickCount())
                .createdAt(urlMapping.getCreatedAt())
                .expiresAt(urlMapping.getExpiresAt())
                .build();
    }

    /**
     * Get all active URLs.
     */
    @Transactional(readOnly = true)
    public List<UrlListItem> getAllUrls() {
        List<UrlMapping> urlMappings = urlMappingRepository.findTop20ByIsActiveTrueOrderByCreatedAtDesc();
        List<UrlListItem> items = new ArrayList<>();
        
        for (UrlMapping mapping : urlMappings) {
            String effectiveKey = mapping.getEffectiveKey();
            String shortUrl = baseUrl + "/" + effectiveKey;
            
            items.add(UrlListItem.builder()
                    .shortKey(mapping.getShortKey())
                    .customAlias(mapping.getCustomAlias())
                    .effectiveKey(effectiveKey)
                    .originalUrl(mapping.getOriginalUrl())
                    .shortUrl(shortUrl)
                    .clickCount(mapping.getClickCount())
                    .createdAt(mapping.getCreatedAt())
                    .expiresAt(mapping.getExpiresAt())
                    .isPasswordProtected(mapping.getIsPasswordProtected())
                    .metaTitle(mapping.getMetaTitle())
                    .aiCategory(mapping.getAiCategory())
                    .build());
        }
        
        return items;
    }

    /**
     * Check if a URL is password protected.
     */
    public boolean isPasswordProtected(String key) {
        return urlMappingRepository.findByShortKeyOrCustomAlias(key, key)
                .map(UrlMapping::getIsPasswordProtected)
                .orElse(false);
    }

    /**
     * Get URL preview info (for password-protected URLs before password entry).
     */
    public UrlMetadata getUrlPreview(String key) {
        UrlMapping urlMapping = urlMappingRepository.findByShortKeyOrCustomAlias(key, key)
                .orElseThrow(() -> new UrlNotFoundException(key));

        return UrlMetadata.builder()
                .title(urlMapping.getMetaTitle())
                .description(urlMapping.getMetaDescription())
                .imageUrl(urlMapping.getMetaImageUrl())
                .faviconUrl(urlMapping.getMetaFaviconUrl())
                .build();
    }

    /**
     * Generates a unique random short key, retrying if collision occurs.
     * Uses cryptographically secure random generation for professional-grade keys.
     *
     * @return a unique random alphanumeric short key (6-8 characters)
     */
    private String generateUniqueShortKey() {
        final int MAX_RETRIES = 10;
        int attempts = 0;
        
        while (attempts < MAX_RETRIES) {
            String key = randomKeyGenerator.generate();
            
            // Check if key already exists (including custom aliases)
            if (!urlMappingRepository.existsByShortKey(key) && 
                !urlMappingRepository.existsByCustomAlias(key)) {
                return key;
            }
            
            attempts++;
            log.debug("Short key collision detected: {}, retrying (attempt {}/{})", key, attempts, MAX_RETRIES);
        }
        
        // If all retries failed, fall back to longer key
        log.warn("Failed to generate unique short key after {} attempts, using longer key", MAX_RETRIES);
        String longerKey = randomKeyGenerator.generate(10);
        while (urlMappingRepository.existsByShortKey(longerKey) || 
               urlMappingRepository.existsByCustomAlias(longerKey)) {
            longerKey = randomKeyGenerator.generate(10);
        }
        return longerKey;
    }
}
