package com.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Standard error response format")
public class ErrorResponse {

    @Schema(
            description = "HTTP status code",
            example = "400"
    )
    private int status;

    @Schema(
            description = "Error type/category",
            example = "Bad Request"
    )
    private String error;

    @Schema(
            description = "Human-readable error message",
            example = "Invalid URL format"
    )
    private String message;

    @Schema(
            description = "Request path that caused the error",
            example = "/api/urls"
    )
    private String path;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(
            description = "When the error occurred (ISO 8601 format)",
            example = "2025-01-15T10:30:00"
    )
    private LocalDateTime timestamp;
}
