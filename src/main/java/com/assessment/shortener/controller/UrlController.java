package com.assessment.shortener.controller;

import com.assessment.shortener.dto.CreateUrlRequest;
import com.assessment.shortener.dto.UrlResponse;
import com.assessment.shortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
public class UrlController {
    private final UrlShortenerService urlShortenerService;

    public UrlController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @PostMapping("/api/v1/urls")
    public ResponseEntity<UrlResponse> create(@Valid @RequestBody CreateUrlRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(urlShortenerService.create(request));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(urlShortenerService.resolve(code)))
                .build();
    }

    @GetMapping("/api/v1/urls/{code}/analytics")
    public UrlResponse analytics(@PathVariable String code) {
        return urlShortenerService.analytics(code);
    }

    @DeleteMapping("/api/v1/urls/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        urlShortenerService.delete(code);
        return ResponseEntity.noContent().build();
    }
}
