package inc.skt.quicklink.controller;

import inc.skt.quicklink.dto.ShortenRequest;
import inc.skt.quicklink.dto.ShortenResponse;
import inc.skt.quicklink.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for URL shortening operations.
 * Handles POST /shorten endpoint.
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
     * POST /shorten
     */
    @PostMapping("/shorten")
    @Operation(summary = "Create short URL", description = "Converts a long URL into a short URL")
    public ResponseEntity<ShortenResponse> shortenUrl(@RequestBody ShortenRequest request) {
        ShortenResponse response = urlService.createShortUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
