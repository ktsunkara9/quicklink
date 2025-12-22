package inc.skt.quicklink.controller;

import inc.skt.quicklink.dto.ShortenRequest;
import inc.skt.quicklink.dto.ShortenResponse;
import inc.skt.quicklink.dto.UpdateUrlRequest;
import inc.skt.quicklink.dto.UrlStatsResponse;
import inc.skt.quicklink.model.UrlMapping;
import inc.skt.quicklink.service.AnalyticsService;
import inc.skt.quicklink.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;

import java.net.URI;

/**
 * REST controller for URL shortening operations.
 * Uses hybrid versioning:
 * - Management endpoints (POST /api/v1/shorten) are versioned for API evolution
 * - Redirect endpoint (GET /{shortCode}) has no version to keep URLs short and stable
 */
@RestController
@Tag(name = "URL Shortener", description = "URL shortening operations")
public class UrlController {
    
    private final UrlService urlService;
    private final AnalyticsService analyticsService;
    
    public UrlController(UrlService urlService, AnalyticsService analyticsService) {
        this.urlService = urlService;
        this.analyticsService = analyticsService;
    }
    
    /**
     * Creates a short URL from a long URL.
     * POST /api/v1/shorten
     */
    @PostMapping("/api/v1/shorten")
    @Operation(summary = "Create short URL", description = "Converts a long URL into a short URL")
    public ResponseEntity<ShortenResponse> shortenUrl(@RequestBody ShortenRequest request, HttpServletRequest httpRequest) {
        // Hardcode stage name since API Gateway requires it
        String scheme = httpRequest.getHeader("X-Forwarded-Proto") != null ? httpRequest.getHeader("X-Forwarded-Proto") : "https";
        String host = httpRequest.getHeader("Host");
        String baseUrl = scheme + "://" + host + "/prod";
        ShortenResponse response = urlService.createShortUrl(request, baseUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Redirects short URL to original URL.
     * Records analytics asynchronously without blocking redirect.
     * GET /{shortCode}
     */
    @GetMapping("/{shortCode}")
    @Operation(summary = "Redirect to original URL", description = "Redirects short URL to the original long URL")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        UrlMapping urlMapping = urlService.getOriginalUrl(shortCode);
        
        // Record analytics asynchronously (non-blocking)
        analyticsService.recordClick(
                shortCode,
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
        
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(urlMapping.getLongUrl()))
                .build();
    }
    
    /**
     * Updates URL properties (expiry time).
     * PATCH /api/v1/urls/{shortCode}
     */
    @PatchMapping("/api/v1/urls/{shortCode}")
    @Operation(summary = "Update URL", description = "Updates URL properties like expiry time")
    public ResponseEntity<ShortenResponse> updateUrl(
            @PathVariable String shortCode,
            @RequestBody UpdateUrlRequest request) {
        ShortenResponse response = urlService.updateUrl(shortCode, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Soft deletes a URL by setting isActive to false.
     * DELETE /api/v1/urls/{shortCode}
     */
    @DeleteMapping("/api/v1/urls/{shortCode}")
    @Operation(summary = "Delete URL", description = "Soft deletes a URL by deactivating it")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode) {
        urlService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Retrieves statistics for a short URL.
     * GET /api/v1/stats/{shortCode}
     */
    @GetMapping("/api/v1/stats/{shortCode}")
    @Operation(summary = "Get URL statistics", description = "Retrieves click count and metadata for a short URL")
    public ResponseEntity<UrlStatsResponse> getStats(@PathVariable String shortCode) {
        UrlStatsResponse response = urlService.getUrlStats(shortCode);
        return ResponseEntity.ok(response);
    }
}
