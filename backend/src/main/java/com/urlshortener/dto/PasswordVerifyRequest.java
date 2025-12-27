package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to verify password for protected URL")
public class PasswordVerifyRequest {

    @NotBlank(message = "Password is required")
    @Schema(description = "The password to verify", example = "secret123")
    private String password;
}

