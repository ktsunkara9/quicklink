package inc.skt.quicklink.controller;

import inc.skt.quicklink.dto.ShortenRequest;
import inc.skt.quicklink.dto.ShortenResponse;
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
        // Extract base URL including stage path (e.g., https://api.example.com/prod)
        String requestUrl = httpRequest.getRequestURL().toString();
        String baseUrl = requestUrl.substring(0, requestUrl.indexOf("/api/v1/shorten"));
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
}
