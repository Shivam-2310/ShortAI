package com.urlshortener.controller;

import com.urlshortener.dto.ErrorResponse;
import com.urlshortener.dto.UrlMetadata;
import com.urlshortener.dto.ai.AiAnalysisResult;
import com.urlshortener.service.OllamaService;
import com.urlshortener.service.UrlMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for AI-powered URL analysis features.
 * Uses local Ollama instance with llama3.2:1b model.
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Analysis", description = "AI-powered URL analysis using local Ollama LLM")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final OllamaService ollamaService;
    private final UrlMetadataService urlMetadataService;

    @Autowired
    public AiController(OllamaService ollamaService, UrlMetadataService urlMetadataService) {
        this.ollamaService = ollamaService;
        this.urlMetadataService = urlMetadataService;
    }

    @Operation(
            summary = "Analyze a URL",
            description = "Performs comprehensive AI analysis on a URL including: summarization, " +
                    "categorization, tag generation, safety scoring, and alias suggestions. " +
                    "Uses local Ollama instance with llama3.2:1b model."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analysis completed",
                    content = @Content(schema = @Schema(implementation = AiAnalysisResult.class))),
            @ApiResponse(responseCode = "400", description = "Invalid URL",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "AI service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/analyze")
    public ResponseEntity<AiAnalysisResult> analyzeUrl(
            @Parameter(description = "The URL to analyze")
            @RequestParam String url) {
        log.info("AI analysis request for URL: {}", url);

        // First fetch metadata
        UrlMetadata metadata = urlMetadataService.fetchMetadata(url);

        // Then perform AI analysis
        AiAnalysisResult result = ollamaService.analyzeUrl(
                url,
                metadata.getTitle(),
                metadata.getDescription()
        );

        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Check URL safety",
            description = "Uses AI to analyze a URL for potential security threats including: " +
                    "phishing, malware, scams, and suspicious patterns."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Safety check completed",
                    content = @Content(schema = @Schema(implementation = AiAnalysisResult.class)))
    })
    @PostMapping("/safety-check")
    public ResponseEntity<AiAnalysisResult> checkSafety(
            @Parameter(description = "The URL to check")
            @RequestParam String url) {
        log.info("Safety check request for URL: {}", url);

        AiAnalysisResult result = ollamaService.checkUrlSafety(url);

        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Generate alias suggestions",
            description = "Uses AI to generate memorable, relevant alias suggestions for a URL."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Suggestions generated")
    })
    @PostMapping("/suggest-alias")
    public ResponseEntity<List<String>> suggestAliases(
            @Parameter(description = "The URL to generate aliases for")
            @RequestParam String url,
            @Parameter(description = "Optional title for context")
            @RequestParam(required = false) String title) {
        log.info("Alias suggestion request for URL: {}", url);

        // Fetch title if not provided
        if (title == null || title.isEmpty()) {
            UrlMetadata metadata = urlMetadataService.fetchMetadata(url);
            title = metadata.getTitle();
        }

        List<String> suggestions = ollamaService.suggestAliases(url, title);

        return ResponseEntity.ok(suggestions);
    }

    @Operation(
            summary = "Summarize URL content",
            description = "Fetches URL content and generates an AI-powered summary."
    )
    @PostMapping("/summarize")
    public ResponseEntity<Map<String, String>> summarizeUrl(
            @Parameter(description = "The URL to summarize")
            @RequestParam String url) {
        log.info("Summarize request for URL: {}", url);

        UrlMetadata metadata = urlMetadataService.fetchMetadata(url);
        String content = metadata.getTextContent();

        String summary = ollamaService.summarizeContent(url, content != null ? content : "");

        return ResponseEntity.ok(Map.of(
                "url", url,
                "title", metadata.getTitle() != null ? metadata.getTitle() : "",
                "summary", summary != null ? summary : "Unable to generate summary"
        ));
    }

    @Operation(
            summary = "Check AI service status",
            description = "Checks if the Ollama AI service is available and responding."
    )
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean available = ollamaService.isAvailable();

        return ResponseEntity.ok(Map.of(
                "available", available,
                "model", "llama3.2:1b",
                "service", "ollama"
        ));
    }
}

