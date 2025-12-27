package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for bulk URL shortening")
public class BulkCreateResponse {

    @Schema(description = "Number of successfully created URLs")
    private int successCount;

    @Schema(description = "Number of failed URLs")
    private int failedCount;

    @Schema(description = "List of created short URLs")
    private List<CreateUrlResponse> results;

    @Schema(description = "List of errors for failed URLs")
    private List<BulkError> errors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Error details for a failed URL")
    public static class BulkError {
        @Schema(description = "Index of the failed URL in the request")
        private int index;

        @Schema(description = "The original URL that failed")
        private String originalUrl;

        @Schema(description = "Error message")
        private String error;
    }
}

