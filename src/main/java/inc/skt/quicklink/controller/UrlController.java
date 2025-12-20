package inc.skt.quicklink.controller;

import inc.skt.quicklink.dto.ShortenRequest;
import inc.skt.quicklink.dto.ShortenResponse;
import inc.skt.quicklink.model.UrlMapping;
import inc.skt.quicklink.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    
    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }
    
    /**
     * Creates a short URL from a long URL.
     * POST /api/v1/shorten
     */
    @PostMapping("/api/v1/shorten")
    @Operation(summary = "Create short URL", description = "Converts a long URL into a short URL")
    public ResponseEntity<ShortenResponse> shortenUrl(@RequestBody ShortenRequest request) {
        ShortenResponse response = urlService.createShortUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Redirects short URL to original URL.
     * GET /{shortCode}
     */
    @GetMapping("/{shortCode}")
    @Operation(summary = "Redirect to original URL", description = "Redirects short URL to the original long URL")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        UrlMapping urlMapping = urlService.getOriginalUrl(shortCode);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(urlMapping.getLongUrl()))
                .build();
    }
}
