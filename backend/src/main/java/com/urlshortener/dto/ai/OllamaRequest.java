package com.urlshortener.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaRequest {

    private String model;
    private String prompt;

    @JsonProperty("stream")
    @Builder.Default
    private Boolean stream = false;

    @JsonProperty("options")
    private OllamaOptions options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OllamaOptions {
        private Integer temperature;
        @JsonProperty("num_predict")
        private Integer numPredict;
        @JsonProperty("top_p")
        private Double topP;
    }
}

