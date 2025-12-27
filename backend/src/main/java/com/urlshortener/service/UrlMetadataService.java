package com.urlshortener.service;

import com.urlshortener.dto.UrlMetadata;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;

/**
 * Service for extracting metadata from URLs.
 * Fetches Open Graph tags, Twitter cards, and basic HTML metadata.
 */
@Service
public class UrlMetadataService {

    private static final Logger log = LoggerFactory.getLogger(UrlMetadataService.class);

    private final int timeoutSeconds;
    private final int maxBodySize;

    public UrlMetadataService(
            @Value("${metadata.fetch-timeout:10}") int timeoutSeconds,
            @Value("${metadata.max-body-size:1048576}") int maxBodySize) {
        this.timeoutSeconds = timeoutSeconds;
        this.maxBodySize = maxBodySize;
    }

    /**
     * Fetch metadata from a URL.
     */
    @CircuitBreaker(name = "metadata", fallbackMethod = "fetchMetadataFallback")
    public UrlMetadata fetchMetadata(String url) {
        log.debug("Fetching metadata for URL: {}", url);

        try {
            Document doc = Jsoup.connect(url)
                    .timeout((int) Duration.ofSeconds(timeoutSeconds).toMillis())
                    .maxBodySize(maxBodySize)
                    .userAgent("Mozilla/5.0 (compatible; URLShortenerBot/2.0)")
                    .followRedirects(true)
                    .get();

            return extractMetadata(doc, url);
        } catch (Exception e) {
            log.warn("Failed to fetch metadata for URL: {}, error: {}", url, e.getMessage());
            return UrlMetadata.builder()
                    .url(url)
                    .build();
        }
    }

    private UrlMetadata extractMetadata(Document doc, String url) {
        UrlMetadata.UrlMetadataBuilder builder = UrlMetadata.builder().url(url);

        // Title (priority: og:title > twitter:title > title tag)
        String title = getMetaContent(doc, "og:title");
        if (title == null) title = getMetaContent(doc, "twitter:title");
        if (title == null) title = doc.title();
        builder.title(title);

        // Description (priority: og:description > twitter:description > meta description)
        String description = getMetaContent(doc, "og:description");
        if (description == null) description = getMetaContent(doc, "twitter:description");
        if (description == null) description = getMetaProperty(doc, "description");
        builder.description(description);

        // Image (priority: og:image > twitter:image)
        String image = getMetaContent(doc, "og:image");
        if (image == null) image = getMetaContent(doc, "twitter:image");
        if (image != null) image = resolveUrl(url, image);
        builder.imageUrl(image);

        // Favicon
        String favicon = extractFavicon(doc, url);
        builder.faviconUrl(favicon);

        // Site name
        String siteName = getMetaContent(doc, "og:site_name");
        builder.siteName(siteName);

        // Type
        String type = getMetaContent(doc, "og:type");
        builder.type(type);

        // Author
        String author = getMetaProperty(doc, "author");
        builder.author(author);

        // Keywords
        String keywords = getMetaProperty(doc, "keywords");
        builder.keywords(keywords);

        // Canonical URL
        Element canonical = doc.selectFirst("link[rel=canonical]");
        if (canonical != null) {
            builder.canonicalUrl(canonical.attr("href"));
        }

        // Plain text content (for AI analysis)
        String textContent = doc.body() != null ? doc.body().text() : "";
        if (textContent.length() > 5000) {
            textContent = textContent.substring(0, 5000);
        }
        builder.textContent(textContent);

        return builder.build();
    }

    private String getMetaContent(Document doc, String property) {
        Element meta = doc.selectFirst("meta[property=" + property + "]");
        if (meta != null) {
            return meta.attr("content");
        }
        meta = doc.selectFirst("meta[name=" + property + "]");
        return meta != null ? meta.attr("content") : null;
    }

    private String getMetaProperty(Document doc, String name) {
        Element meta = doc.selectFirst("meta[name=" + name + "]");
        return meta != null ? meta.attr("content") : null;
    }

    private String extractFavicon(Document doc, String url) {
        // Try various favicon link elements
        String[] selectors = {
                "link[rel='icon']",
                "link[rel='shortcut icon']",
                "link[rel='apple-touch-icon']",
                "link[rel='apple-touch-icon-precomposed']"
        };

        for (String selector : selectors) {
            Element link = doc.selectFirst(selector);
            if (link != null && link.hasAttr("href")) {
                return resolveUrl(url, link.attr("href"));
            }
        }

        // Default to /favicon.ico
        try {
            URI uri = new URI(url);
            return uri.getScheme() + "://" + uri.getHost() + "/favicon.ico";
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUrl(String baseUrl, String relativeUrl) {
        if (relativeUrl == null || relativeUrl.isEmpty()) {
            return null;
        }
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        if (relativeUrl.startsWith("//")) {
            return "https:" + relativeUrl;
        }
        try {
            URI base = new URI(baseUrl);
            return base.resolve(relativeUrl).toString();
        } catch (Exception e) {
            return relativeUrl;
        }
    }

    // Fallback for circuit breaker
    public UrlMetadata fetchMetadataFallback(String url, Throwable t) {
        log.warn("Metadata fetch fallback triggered for URL: {}, reason: {}", url, t.getMessage());
        return UrlMetadata.builder()
                .url(url)
                .title(null)
                .description(null)
                .build();
    }
}

