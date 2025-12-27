package com.urlshortener.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.ai.AiAnalysisResult;
import com.urlshortener.dto.ai.OllamaRequest;
import com.urlshortener.dto.ai.OllamaResponse;
import com.urlshortener.entity.AiAnalysisCache;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.repository.AiAnalysisCacheRepository;
import com.urlshortener.repository.UrlMappingRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production-grade service for AI-powered URL analysis using local Ollama instance.
 * Features:
 * - Async processing to avoid blocking URL creation
 * - Robust JSON parsing with multiple fallback strategies
 * - Comprehensive error handling and fallbacks
 * - Response validation and sanitization
 * - Health checks before processing
 * - Improved prompt engineering
 * - Circuit breaker and retry mechanisms
 */
@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private static final List<String> VALID_CATEGORIES = List.of(
            "Technology", "News", "Entertainment", "Education", "Business",
            "Social", "Shopping", "Health", "Travel", "Finance", "Sports", "Other"
    );

    private final WebClient ollamaClient;
    private final AiAnalysisCacheRepository cacheRepository;
    private final UrlMappingRepository urlMappingRepository;
    private final ObjectMapper objectMapper;
    private final String modelName;
    private final int cacheDays;
    private volatile boolean ollamaAvailable = true;
    private volatile long lastHealthCheck = 0;
    private static final long HEALTH_CHECK_INTERVAL_MS = 30000; // 30 seconds

    @Autowired
    public OllamaService(
            WebClient.Builder webClientBuilder,
            AiAnalysisCacheRepository cacheRepository,
            UrlMappingRepository urlMappingRepository,
            ObjectMapper objectMapper,
            @Value("${ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${ollama.model:llama3.2:1b}") String modelName,
            @Value("${ollama.cache-days:7}") int cacheDays) {
        this.ollamaClient = webClientBuilder
                .baseUrl(ollamaBaseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
        this.cacheRepository = cacheRepository;
        this.urlMappingRepository = urlMappingRepository;
        this.objectMapper = objectMapper;
        this.modelName = modelName;
        this.cacheDays = cacheDays;
    }

    /**
     * Analyze a URL using AI with caching and async processing.
     * This method checks cache first, then performs async analysis if needed.
     */
    @CircuitBreaker(name = "ollama", fallbackMethod = "analyzeUrlFallback")
    @Retry(name = "ollama")
    public AiAnalysisResult analyzeUrl(String url, String pageTitle, String pageDescription) {
        String urlHash = hashUrl(url);

        // Check cache first
        Optional<AiAnalysisCache> cached = cacheRepository.findByUrlHash(urlHash);
        if (cached.isPresent() && !cached.get().isExpired()) {
            log.debug("AI analysis cache hit for URL: {}", url);
            return mapCacheToResult(cached.get(), true);
        }

        // Check if Ollama is available before attempting
        if (!checkOllamaAvailability()) {
            log.warn("Ollama is not available, returning default analysis");
            return getDefaultAnalysis(url);
        }

        log.info("Performing AI analysis for URL: {}", url);

        // Build improved prompt
        String prompt = buildImprovedAnalysisPrompt(url, pageTitle, pageDescription);

        try {
            OllamaResponse response = callOllamaWithRetry(prompt);
            if (response == null || response.getResponse() == null) {
                log.warn("Empty response from Ollama for URL: {}", url);
                return getDefaultAnalysis(url);
            }

            AiAnalysisResult result = parseAnalysisResponseRobust(response.getResponse(), url);

            // Validate and sanitize result
            result = validateAndSanitizeResult(result, url);

            // Cache the result
            saveToCache(urlHash, url, result);

            return result;
        } catch (Exception e) {
            log.error("Error during AI analysis for URL: {}", url, e);
            ollamaAvailable = false; // Mark as unavailable on error
            return getDefaultAnalysis(url);
        }
    }

    /**
     * Async version for background processing after URL creation.
     */
    @Async("analyticsExecutor")
    @Transactional
    public void analyzeUrlAsync(Long urlMappingId, String url, String pageTitle, String pageDescription) {
        try {
            Optional<UrlMapping> mappingOpt = urlMappingRepository.findById(urlMappingId);
            if (mappingOpt.isEmpty()) {
                log.warn("URL mapping not found for async analysis: {}", urlMappingId);
                return;
            }

            UrlMapping mapping = mappingOpt.get();
            
            // Skip if already analyzed
            if (mapping.getAiAnalyzedAt() != null) {
                log.debug("URL already analyzed, skipping: {}", url);
                return;
            }

            log.info("Starting async AI analysis for URL: {}", url);
            AiAnalysisResult result = analyzeUrl(url, pageTitle, pageDescription);

            if (result != null && !result.getFromCache()) {
                // Update the URL mapping with AI results
                mapping.setAiSummary(result.getSummary());
                mapping.setAiCategory(result.getCategory());
                mapping.setAiTags(result.getTags() != null ? String.join(",", result.getTags()) : null);
                mapping.setAiSafetyScore(result.getSafetyScore());
                mapping.setAiAnalyzedAt(LocalDateTime.now());
                urlMappingRepository.save(mapping);
                log.info("Async AI analysis completed for URL: {}", url);
            }
        } catch (Exception e) {
            log.error("Error in async AI analysis for URL: {}", url, e);
        }
    }

    /**
     * Generate custom alias suggestions for a URL.
     */
    @CircuitBreaker(name = "ollama", fallbackMethod = "suggestAliasesFallback")
    public List<String> suggestAliases(String url, String title) {
        if (!checkOllamaAvailability()) {
            return List.of();
        }

        String prompt = String.format("""
            You are a URL shortener assistant. Generate 5 short, memorable URL aliases.
            
            URL: %s
            Title: %s
            
            Rules:
            - Each alias: 3-15 characters
            - Only lowercase letters, numbers, hyphens
            - Memorable and relevant to content
            - No spaces or special characters
            - Return ONLY aliases, one per line
            
            Examples:
            github -> github-dev, code-hub, git-link
            news -> daily-news, news-today
            
            Aliases:
            """, url, title != null ? title : "Unknown");

        try {
            OllamaResponse response = callOllamaWithRetry(prompt);
            if (response == null) {
                return List.of();
            }
            return parseAliases(response.getResponse());
        } catch (Exception e) {
            log.error("Error generating alias suggestions", e);
            return List.of();
        }
    }

    /**
     * Check if a URL appears to be safe.
     */
    @CircuitBreaker(name = "ollama", fallbackMethod = "checkSafetyFallback")
    public AiAnalysisResult checkUrlSafety(String url) {
        if (!checkOllamaAvailability()) {
            return getDefaultSafetyResult();
        }

        String prompt = String.format("""
            Analyze this URL for safety issues. Check for:
            1. Phishing (misspelled domains, suspicious patterns)
            2. Malware distribution patterns
            3. Suspicious URL structure
            4. Scam indicators
            
            URL: %s
            
            Respond with ONLY valid JSON in this exact format:
            {
                "safetyScore": 0.0-1.0,
                "isSafe": true/false,
                "reasons": ["reason1", "reason2"]
            }
            
            JSON:
            """, url);

        try {
            OllamaResponse response = callOllamaWithRetry(prompt);
            if (response == null) {
                return getDefaultSafetyResult();
            }
            return parseSafetyResponse(response.getResponse(), url);
        } catch (Exception e) {
            log.error("Error checking URL safety", e);
            return getDefaultSafetyResult();
        }
    }

    /**
     * Generate a smart summary for a URL.
     */
    @CircuitBreaker(name = "ollama", fallbackMethod = "summarizeFallback")
    public String summarizeContent(String url, String content) {
        if (!checkOllamaAvailability()) {
            return null;
        }

        String truncatedContent = content.length() > 2000 ? content.substring(0, 2000) + "..." : content;

        String prompt = String.format("""
            Summarize this webpage in 1-2 sentences. Be concise and informative.
            
            URL: %s
            Content: %s
            
            Summary:
            """, url, truncatedContent);

        try {
            OllamaResponse response = callOllamaWithRetry(prompt);
            if (response == null || response.getResponse() == null) {
                return null;
            }
            return response.getResponse().trim();
        } catch (Exception e) {
            log.error("Error summarizing content", e);
            return null;
        }
    }

    /**
     * Improved Ollama call with reactive retry.
     */
    private OllamaResponse callOllamaWithRetry(String prompt) {
        OllamaRequest request = OllamaRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .stream(false)
                .options(OllamaRequest.OllamaOptions.builder()
                        .temperature(0) // Lower temperature for more consistent JSON (0 = deterministic)
                        .numPredict(1000) // Increased to prevent truncation
                        .topP(0.9)
                        .build())
                .build();

        try {
            return ollamaClient.post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofSeconds(45)) // Increased timeout
                    .retryWhen(reactor.util.retry.Retry.backoff(2, Duration.ofSeconds(1))
                            .filter(throwable -> throwable instanceof java.util.concurrent.TimeoutException))
                    .doOnError(error -> {
                        log.warn("Ollama call failed: {}", error.getMessage());
                        ollamaAvailable = false;
                    })
                    .block();
        } catch (Exception e) {
            log.error("Failed to call Ollama", e);
            ollamaAvailable = false;
            throw new RuntimeException("Ollama service unavailable", e);
        }
    }

    /**
     * Improved prompt with better structure for JSON generation.
     */
    private String buildImprovedAnalysisPrompt(String url, String title, String description) {
        return String.format("""
            Analyze this URL and provide a comprehensive analysis. You MUST respond with ONLY valid JSON, no explanations, no markdown.
            
            URL: %s
            Title: %s
            Description: %s
            
            Analyze the URL and provide:
            1. A brief 1-2 sentence summary of what this website/service is about
            2. The most appropriate category from: Technology, News, Entertainment, Education, Business, Social, Shopping, Health, Travel, Finance, Sports, Other
            3. 3-5 relevant tags that describe the content/topic
            4. A safety score between 0.0 and 1.0 (1.0 = completely safe, 0.0 = dangerous)
            5. Whether it's safe (true/false)
            6. Any safety concerns as an array (empty if safe)
            7. 3-5 short, memorable alias suggestions (2-4 words max, URL-friendly)
            
            Respond with ONLY this JSON structure:
            {
                "summary": "your actual summary here",
                "category": "one of the categories listed above",
                "tags": ["relevant", "tags", "here"],
                "safetyScore": 0.95,
                "isSafe": true,
                "safetyReasons": [],
                "aliasSuggestions": ["short-alias-1", "short-alias-2", "short-alias-3"]
            }
            
            Important: Generate REAL content based on the URL, title, and description. Do NOT use placeholder text.
            """,
                url,
                title != null && !title.isEmpty() ? title : "Unknown",
                description != null && !description.isEmpty() ? description : "No description available"
        );
    }

    /**
     * Robust JSON parsing with multiple extraction strategies.
     */
    private AiAnalysisResult parseAnalysisResponseRobust(String response, String url) {
        if (response == null || response.trim().isEmpty()) {
            log.warn("Empty response from AI for URL: {}", url);
            return getDefaultAnalysis(url);
        }

        log.debug("Raw AI response for URL {}: {}", url, response.substring(0, Math.min(500, response.length())));

        // Strategy 1: Try to find JSON object using regex
        String jsonStr = extractJsonFromResponse(response);
        
        // Strategy 2: Try parsing as-is
        if (jsonStr == null || jsonStr.isEmpty()) {
            jsonStr = response.trim();
        }

        // Strategy 3: Try to extract from markdown code blocks
        if (!jsonStr.contains("{")) {
            jsonStr = extractFromMarkdown(jsonStr);
        }

        // Strategy 4: Try to repair incomplete JSON
        if (jsonStr != null && !jsonStr.trim().isEmpty()) {
            jsonStr = repairIncompleteJson(jsonStr);
        }

        try {
            JsonNode json = objectMapper.readTree(jsonStr);
            AiAnalysisResult result = buildAnalysisResultFromJson(json, url);
            log.info("Successfully parsed AI analysis for URL {}: category={}, summary={}", 
                    url, result.getCategory(), result.getSummary().substring(0, Math.min(50, result.getSummary().length())));
            return result;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON for URL {}, attempting manual extraction. Error: {}", url, e.getMessage());
            log.debug("Failed JSON string: {}", jsonStr);
            return parseManually(response, url);
        }
    }

    /**
     * Extract JSON from response using multiple strategies.
     */
    private String extractJsonFromResponse(String response) {
        // Try regex pattern
        Matcher matcher = JSON_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group(0);
        }

        // Try finding first { and last }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        return null;
    }

    /**
     * Extract JSON from markdown code blocks.
     */
    private String extractFromMarkdown(String text) {
        // Remove markdown code blocks
        text = text.replaceAll("```json\\s*", "");
        text = text.replaceAll("```\\s*", "");
        text = text.trim();
        
        // Try to find JSON
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        
        return text;
    }

    /**
     * Attempt to repair incomplete JSON by closing unclosed structures.
     */
    private String repairIncompleteJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return jsonStr;
        }

        String repaired = jsonStr.trim();
        
        // Count braces to see if JSON is incomplete
        long openBraces = repaired.chars().filter(ch -> ch == '{').count();
        long closeBraces = repaired.chars().filter(ch -> ch == '}').count();
        long openBrackets = repaired.chars().filter(ch -> ch == '[').count();
        long closeBrackets = repaired.chars().filter(ch -> ch == ']').count();
        
        // If JSON seems incomplete, try to close it
        if (openBraces > closeBraces) {
            // Find the last unclosed structure
            int lastOpenBrace = repaired.lastIndexOf('{');
            if (lastOpenBrace >= 0) {
                // Check if there's a trailing comma before the last quote
                if (repaired.endsWith(",") || repaired.endsWith("\"")) {
                    repaired = repaired.substring(0, repaired.length() - (repaired.endsWith(",") ? 1 : 0));
                }
                // Close remaining structures
                for (long i = closeBraces; i < openBraces; i++) {
                    repaired += "}";
                }
            }
        }
        
        if (openBrackets > closeBrackets) {
            for (long i = closeBrackets; i < openBrackets; i++) {
                repaired += "]";
            }
        }
        
        // Remove trailing commas before closing braces/brackets
        repaired = repaired.replaceAll(",\\s*}", "}");
        repaired = repaired.replaceAll(",\\s*]", "]");
        
        return repaired;
    }

    /**
     * Build analysis result from parsed JSON.
     */
    private AiAnalysisResult buildAnalysisResultFromJson(JsonNode json, String url) {
        return AiAnalysisResult.builder()
                .summary(sanitizeText(getTextOrDefault(json, "summary", "No summary available")))
                .category(validateCategory(getTextOrDefault(json, "category", "Other")))
                .tags(sanitizeTags(getListOrDefault(json, "tags")))
                .safetyScore(validateSafetyScore(getDecimalOrDefault(json, "safetyScore", new BigDecimal("0.8"))))
                .isSafe(getBooleanOrDefault(json, "isSafe", true))
                .safetyReasons(sanitizeList(getListOrDefault(json, "safetyReasons")))
                .aliasSuggestions(sanitizeAliases(getListOrDefault(json, "aliasSuggestions")))
                .fromCache(false)
                .build();
    }

    /**
     * Manual parsing fallback when JSON parsing fails.
     */
    private AiAnalysisResult parseManually(String response, String url) {
        log.info("Attempting manual parsing of AI response for URL: {}", url);
        log.debug("Full response for manual parsing: {}", response);
        
        // Try to extract key information using improved regex patterns
        String summary = extractField(response, "summary", "No summary available");
        String category = extractCategoryField(response);
        
        log.info("Manually extracted - Category: {}, Summary: {}", category, summary.substring(0, Math.min(50, summary.length())));
        
        // Try to extract tags
        List<String> tags = extractTagsField(response);
        
        // Try to extract alias suggestions
        List<String> aliases = extractAliasSuggestionsField(response);
        
        // Try to extract safety score
        BigDecimal safetyScore = extractSafetyScoreField(response);
        
        return AiAnalysisResult.builder()
                .summary(sanitizeText(summary))
                .category(category)
                .tags(tags)
                .safetyScore(safetyScore)
                .isSafe(true)
                .safetyReasons(List.of())
                .aliasSuggestions(aliases)
                .fromCache(false)
                .build();
    }

    private String extractField(String text, String field, String defaultValue) {
        // Improved regex to handle various JSON formats
        // Pattern 1: "field": "value"
        Pattern pattern1 = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher matcher1 = pattern1.matcher(text);
        if (matcher1.find()) {
            return matcher1.group(1).trim();
        }
        
        // Pattern 2: "field": 'value'
        Pattern pattern2 = Pattern.compile("\"" + field + "\"\\s*:\\s*'([^']+)'", Pattern.CASE_INSENSITIVE);
        Matcher matcher2 = pattern2.matcher(text);
        if (matcher2.find()) {
            return matcher2.group(1).trim();
        }
        
        // Pattern 3: field: "value" or field: value
        Pattern pattern3 = Pattern.compile(field + "\\s*:\\s*[\"']?([^,\"'}]+)[\"']?", Pattern.CASE_INSENSITIVE);
        Matcher matcher3 = pattern3.matcher(text);
        if (matcher3.find()) {
            return matcher3.group(1).trim();
        }
        
        return defaultValue;
    }

    private String extractCategoryField(String text) {
        String category = extractField(text, "category", null);
        if (category != null) {
            return validateCategory(category);
        }
        
        // Try to find category even if JSON is malformed
        // Look for patterns like: category":"Technology" or category: Technology
        Pattern categoryPattern = Pattern.compile("category[\"']?\\s*:\\s*[\"']?([A-Za-z]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = categoryPattern.matcher(text);
        if (matcher.find()) {
            String found = matcher.group(1).trim();
            log.debug("Found category in text: {}", found);
            return validateCategory(found);
        }
        
        log.warn("Could not extract category from response, defaulting to Other");
        return "Other";
    }

    private List<String> extractTagsField(String text) {
        List<String> tags = new ArrayList<>();
        // Try to extract array: "tags":["tag1","tag2"]
        Pattern pattern = Pattern.compile("\"tags\"\\s*:\\s*\\[([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String tagsStr = matcher.group(1);
            // Extract individual tags
            Pattern tagPattern = Pattern.compile("[\"']([^,\"']+)[\"']");
            Matcher tagMatcher = tagPattern.matcher(tagsStr);
            while (tagMatcher.find()) {
                String tag = tagMatcher.group(1).trim();
                if (!tag.isEmpty() && !tag.equalsIgnoreCase("tag1") && !tag.equalsIgnoreCase("tag2")) {
                    tags.add(tag);
                }
            }
        }
        return tags.isEmpty() ? List.of() : tags;
    }

    private List<String> extractAliasSuggestionsField(String text) {
        List<String> aliases = new ArrayList<>();
        // Try to extract array: "aliasSuggestions":["alias1","alias2"]
        Pattern pattern = Pattern.compile("\"aliasSuggestions\"\\s*:\\s*\\[([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String aliasesStr = matcher.group(1);
            // Extract individual aliases
            Pattern aliasPattern = Pattern.compile("[\"']([^,\"']+)[\"']");
            Matcher aliasMatcher = aliasPattern.matcher(aliasesStr);
            while (aliasMatcher.find()) {
                String alias = aliasMatcher.group(1).trim();
                // Filter out placeholder values
                if (!alias.isEmpty() && !alias.equalsIgnoreCase("alias1") && !alias.equalsIgnoreCase("alias2")) {
                    aliases.add(alias);
                }
            }
        }
        return aliases.isEmpty() ? List.of() : aliases;
    }

    private BigDecimal extractSafetyScoreField(String text) {
        String scoreStr = extractField(text, "safetyScore", null);
        if (scoreStr != null) {
            try {
                return validateSafetyScore(new BigDecimal(scoreStr));
            } catch (NumberFormatException e) {
                log.debug("Could not parse safety score: {}", scoreStr);
            }
        }
        return new BigDecimal("0.8");
    }

    private AiAnalysisResult parseSafetyResponse(String response, String url) {
        try {
            String jsonStr = extractJsonFromResponse(response);
            if (jsonStr == null) {
                jsonStr = extractFromMarkdown(response);
            }
            
            JsonNode json = objectMapper.readTree(jsonStr);
            
            return AiAnalysisResult.builder()
                    .safetyScore(validateSafetyScore(getDecimalOrDefault(json, "safetyScore", new BigDecimal("0.8"))))
                    .isSafe(getBooleanOrDefault(json, "isSafe", true))
                    .safetyReasons(sanitizeList(getListOrDefault(json, "reasons")))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse safety response: {}", e.getMessage());
            return getDefaultSafetyResult();
        }
    }

    private List<String> parseAliases(String response) {
        List<String> aliases = new ArrayList<>();
        String[] lines = response.split("\\n");
        for (String line : lines) {
            String cleaned = line.trim()
                    .toLowerCase()
                    .replaceAll("[^a-z0-9-]", "")
                    .replaceAll("^-+|-+$", ""); // Remove leading/trailing hyphens
            
            if (!cleaned.isEmpty() && cleaned.length() >= 3 && cleaned.length() <= 15) {
                aliases.add(cleaned);
            }
            if (aliases.size() >= 5) break;
        }
        return aliases;
    }

    // Validation and sanitization methods
    private String validateCategory(String category) {
        if (category == null || category.isEmpty()) {
            log.debug("Category is null or empty, defaulting to Other");
            return "Other";
        }
        String normalized = category.trim();
        
        // Remove quotes if present
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.startsWith("'") && normalized.endsWith("'")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized.trim();
        
        // Check if it matches a valid category (case-insensitive)
        for (String valid : VALID_CATEGORIES) {
            if (valid.equalsIgnoreCase(normalized)) {
                log.debug("Category '{}' matched valid category '{}'", category, valid);
                return valid;
            }
        }
        
        // Try fuzzy matching for common variations
        String lowerNormalized = normalized.toLowerCase();
        if (lowerNormalized.contains("tech")) {
            log.debug("Category '{}' fuzzy matched to Technology", category);
            return "Technology";
        } else if (lowerNormalized.contains("news") || lowerNormalized.contains("journalism")) {
            log.debug("Category '{}' fuzzy matched to News", category);
            return "News";
        } else if (lowerNormalized.contains("entertain") || lowerNormalized.contains("media") || lowerNormalized.contains("video")) {
            log.debug("Category '{}' fuzzy matched to Entertainment", category);
            return "Entertainment";
        } else if (lowerNormalized.contains("educat") || lowerNormalized.contains("learn") || lowerNormalized.contains("course")) {
            log.debug("Category '{}' fuzzy matched to Education", category);
            return "Education";
        } else if (lowerNormalized.contains("business") || lowerNormalized.contains("corporate") || lowerNormalized.contains("company")) {
            log.debug("Category '{}' fuzzy matched to Business", category);
            return "Business";
        } else if (lowerNormalized.contains("social") || lowerNormalized.contains("network")) {
            log.debug("Category '{}' fuzzy matched to Social", category);
            return "Social";
        } else if (lowerNormalized.contains("shop") || lowerNormalized.contains("store") || lowerNormalized.contains("ecommerce")) {
            log.debug("Category '{}' fuzzy matched to Shopping", category);
            return "Shopping";
        } else if (lowerNormalized.contains("health") || lowerNormalized.contains("medical") || lowerNormalized.contains("wellness")) {
            log.debug("Category '{}' fuzzy matched to Health", category);
            return "Health";
        } else if (lowerNormalized.contains("travel") || lowerNormalized.contains("tourism") || lowerNormalized.contains("hotel")) {
            log.debug("Category '{}' fuzzy matched to Travel", category);
            return "Travel";
        } else if (lowerNormalized.contains("finance") || lowerNormalized.contains("bank") || lowerNormalized.contains("money") || lowerNormalized.contains("invest")) {
            log.debug("Category '{}' fuzzy matched to Finance", category);
            return "Finance";
        } else if (lowerNormalized.contains("sport")) {
            log.debug("Category '{}' fuzzy matched to Sports", category);
            return "Sports";
        }
        
        log.warn("Category '{}' did not match any valid category, defaulting to Other", category);
        return "Other";
    }

    private BigDecimal validateSafetyScore(BigDecimal score) {
        if (score == null) {
            return new BigDecimal("0.8");
        }
        // Clamp between 0 and 1
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return score;
    }

    private String sanitizeText(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        // Remove control characters, limit length
        String cleaned = text.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
        // Filter out placeholder text
        if (cleaned.equalsIgnoreCase("Brief description") || 
            cleaned.equalsIgnoreCase("No summary available") ||
            cleaned.length() < 10) {
            return "";
        }
        return cleaned.substring(0, Math.min(cleaned.length(), 500));
    }

    private List<String> sanitizeTags(List<String> tags) {
        if (tags == null) return List.of();
        return tags.stream()
                .filter(tag -> tag != null && !tag.trim().isEmpty())
                .map(tag -> sanitizeText(tag))
                .limit(10)
                .toList();
    }

    private List<String> sanitizeList(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
                .filter(item -> item != null && !item.trim().isEmpty())
                .map(item -> sanitizeText(item))
                .limit(5)
                .toList();
    }

    private List<String> sanitizeAliases(List<String> aliases) {
        if (aliases == null) return List.of();
        return aliases.stream()
                .filter(alias -> alias != null && !alias.trim().isEmpty())
                .map(alias -> {
                    // Clean alias: lowercase, replace spaces with hyphens, remove invalid chars
                    String cleaned = alias.trim()
                            .toLowerCase()
                            .replaceAll("\\s+", "-")  // Replace spaces with hyphens
                            .replaceAll("[^a-z0-9-]", "")  // Remove invalid characters
                            .replaceAll("-+", "-")  // Replace multiple hyphens with single
                            .replaceAll("^-+|-+$", "");  // Remove leading/trailing hyphens
                    return cleaned;
                })
                .filter(alias -> alias.length() >= 3 && alias.length() <= 20)  // Reasonable length
                .distinct()  // Remove duplicates
                .limit(5)
                .toList();
    }

    private AiAnalysisResult validateAndSanitizeResult(AiAnalysisResult result, String url) {
        if (result == null) {
            return getDefaultAnalysis(url);
        }

        return AiAnalysisResult.builder()
                .summary(result.getSummary() != null ? sanitizeText(result.getSummary()) : "No summary available")
                .category(validateCategory(result.getCategory()))
                .tags(sanitizeTags(result.getTags()))
                .safetyScore(validateSafetyScore(result.getSafetyScore()))
                .isSafe(result.getIsSafe() != null ? result.getIsSafe() : true)
                .safetyReasons(sanitizeList(result.getSafetyReasons()))
                .aliasSuggestions(sanitizeAliases(result.getAliasSuggestions()))
                .fromCache(result.getFromCache())
                .build();
    }

    // Helper methods
    private String getTextOrDefault(JsonNode json, String field, String defaultValue) {
        return json.has(field) && !json.get(field).isNull() ? json.get(field).asText() : defaultValue;
    }

    private List<String> getListOrDefault(JsonNode json, String field) {
        if (json.has(field) && json.get(field).isArray()) {
            List<String> result = new ArrayList<>();
            json.get(field).forEach(node -> result.add(node.asText()));
            return result;
        }
        return List.of();
    }

    private BigDecimal getDecimalOrDefault(JsonNode json, String field, BigDecimal defaultValue) {
        if (json.has(field) && !json.get(field).isNull()) {
            try {
                if (json.get(field).isNumber()) {
                    return json.get(field).decimalValue();
                }
                return new BigDecimal(json.get(field).asText());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean getBooleanOrDefault(JsonNode json, String field, boolean defaultValue) {
        return json.has(field) && !json.get(field).isNull() ? json.get(field).asBoolean() : defaultValue;
    }

    private AiAnalysisResult getDefaultAnalysis(String url) {
        return AiAnalysisResult.builder()
                .summary("AI analysis is currently unavailable. Please try again later.")
                .category("Other")
                .tags(List.of())
                .safetyScore(new BigDecimal("0.8"))
                .isSafe(true)
                .safetyReasons(List.of())
                .aliasSuggestions(List.of())
                .fromCache(false)
                .build();
    }

    private AiAnalysisResult getDefaultSafetyResult() {
        return AiAnalysisResult.builder()
                .safetyScore(new BigDecimal("0.5"))
                .isSafe(true)
                .safetyReasons(List.of("AI safety check unavailable"))
                .build();
    }

    /**
     * Check Ollama availability with caching.
     */
    private boolean checkOllamaAvailability() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheck < HEALTH_CHECK_INTERVAL_MS) {
            return ollamaAvailable;
        }

        lastHealthCheck = now;
        try {
            String response = ollamaClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            
            if (response != null && response.contains("models")) {
                ollamaAvailable = true;
                log.debug("Ollama health check passed");
                return true;
            } else {
                log.warn("Ollama health check returned unexpected response");
                ollamaAvailable = false;
                return false;
            }
        } catch (Exception e) {
            log.warn("Ollama health check failed: {}", e.getMessage());
            ollamaAvailable = false;
            return false;
        }
    }

    @Transactional
    protected void saveToCache(String urlHash, String url, AiAnalysisResult result) {
        try {
            // Delete old cache entry if exists
            cacheRepository.findByUrlHash(urlHash).ifPresent(cacheRepository::delete);

            AiAnalysisCache cache = AiAnalysisCache.builder()
                    .urlHash(urlHash)
                    .originalUrl(url)
                    .summary(result.getSummary())
                    .category(result.getCategory())
                    .tags(result.getTags() != null ? String.join(",", result.getTags()) : null)
                    .safetyScore(result.getSafetyScore())
                    .isSafe(result.getIsSafe())
                    .safetyReasons(result.getSafetyReasons() != null ? String.join(",", result.getSafetyReasons()) : null)
                    .analyzedAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(cacheDays))
                    .build();

            cacheRepository.save(cache);
            log.debug("Cached AI analysis for URL hash: {}", urlHash);
        } catch (Exception e) {
            log.warn("Failed to cache AI analysis: {}", e.getMessage());
        }
    }

    private AiAnalysisResult mapCacheToResult(AiAnalysisCache cache, boolean fromCache) {
        return AiAnalysisResult.builder()
                .summary(cache.getSummary())
                .category(cache.getCategory())
                .tags(cache.getTags() != null ? Arrays.asList(cache.getTags().split(",")) : List.of())
                .safetyScore(cache.getSafetyScore())
                .isSafe(cache.getIsSafe())
                .safetyReasons(cache.getSafetyReasons() != null ?
                        Arrays.asList(cache.getSafetyReasons().split(",")) : List.of())
                .fromCache(fromCache)
                .build();
    }

    private String hashUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(url.hashCode());
        }
    }

    // Fallback methods for circuit breaker
    public AiAnalysisResult analyzeUrlFallback(String url, String pageTitle, String pageDescription, Throwable t) {
        log.warn("AI analysis fallback triggered for URL: {}, reason: {}", url, t.getMessage());
        ollamaAvailable = false;
        return getDefaultAnalysis(url);
    }

    public List<String> suggestAliasesFallback(String url, String title, Throwable t) {
        log.warn("Alias suggestion fallback triggered, reason: {}", t.getMessage());
        return List.of();
    }

    public AiAnalysisResult checkSafetyFallback(String url, Throwable t) {
        log.warn("Safety check fallback triggered for URL: {}, reason: {}", url, t.getMessage());
        return getDefaultSafetyResult();
    }

    public String summarizeFallback(String url, String content, Throwable t) {
        log.warn("Summarize fallback triggered, reason: {}", t.getMessage());
        return null;
    }

    /**
     * Public method to check if Ollama is available.
     */
    public boolean isAvailable() {
        return checkOllamaAvailability();
    }
}
