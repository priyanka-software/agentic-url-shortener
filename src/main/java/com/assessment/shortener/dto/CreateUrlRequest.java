package com.assessment.shortener.dto;
import jakarta.validation.constraints.*;
public record CreateUrlRequest(@NotBlank @Size(max=2048) String url, @Min(1) @Max(525600) Long expiresInMinutes) {}
