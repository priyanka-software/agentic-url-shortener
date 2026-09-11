package com.assessment.shortener.dto;
import java.time.Instant;
public record UrlResponse(String shortCode,String shortUrl,String originalUrl,Instant createdAt,Instant expiresAt,long clickCount) {}
