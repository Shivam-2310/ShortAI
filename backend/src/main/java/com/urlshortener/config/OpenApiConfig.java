package com.urlshortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public OpenAPI urlShortenerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI-Powered URL Shortener API")
                        .description("""
                                Production-ready URL Shortener with AI-powered features.
                                
                                ## Features
                                - **URL Shortening** with custom aliases
                                - **AI Analysis** using local Ollama LLM (llama3.2:1b)
                                - **QR Code Generation** with custom colors
                                - **Password Protection** for sensitive links
                                - **Bulk URL Creation** (up to 100 at once)
                                - **Detailed Analytics** (geo, device, referrer tracking)
                                - **Metadata Extraction** (Open Graph, Twitter Cards)
                                
                                ## AI Capabilities
                                - Smart URL summarization
                                - Automatic categorization
                                - Tag generation
                                - Safety/phishing detection
                                - Memorable alias suggestions
                                
                                ## Rate Limiting
                                - 100 requests per minute per IP on redirect endpoints
                                """)
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@urlshortener.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url(baseUrl)
                                .description("URL Shortener Server")
                ))
                .tags(List.of(
                        new Tag().name("URL Shortener").description("Create and manage shortened URLs"),
                        new Tag().name("Redirect").description("URL redirect endpoints"),
                        new Tag().name("AI Analysis").description("AI-powered URL analysis using Ollama LLM")
                ));
    }
}
