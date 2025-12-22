package inc.skt.quicklink.service;

import inc.skt.quicklink.dto.ShortenRequest;
import inc.skt.quicklink.dto.ShortenResponse;
import inc.skt.quicklink.exception.AliasAlreadyExistsException;
import inc.skt.quicklink.exception.InvalidAliasException;
import inc.skt.quicklink.exception.InvalidUrlException;
import inc.skt.quicklink.exception.UrlExpiredException;
import inc.skt.quicklink.exception.UrlNotFoundException;
import inc.skt.quicklink.model.UrlMapping;
import inc.skt.quicklink.repository.UrlRepository;
import inc.skt.quicklink.util.Base62Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service layer for URL shortening business logic.
 * Handles URL creation, retrieval, and validation.
 */
@Service
public class UrlService {
    
    private static final Logger log = LoggerFactory.getLogger(UrlService.class);
    
    private final UrlRepository urlRepository;
    private final TokenService tokenService;
    
    @Value("${app.short-domain:}")
    private String shortDomain;
    
    public UrlService(UrlRepository urlRepository, TokenService tokenService) {
        this.urlRepository = urlRepository;
        this.tokenService = tokenService;
        // If SHORT_DOMAIN not set, derive from request context at runtime
        if (shortDomain == null || shortDomain.isEmpty()) {
            shortDomain = "https://skt.inc";
        }
    }
    
    /**
     * Creates a short URL from a long URL.
     * If customAlias is provided, uses it as the short code.
     * Otherwise, auto-generates a short code using TokenService.
     */
    public ShortenResponse createShortUrl(ShortenRequest request) {
        return createShortUrl(request, null);
    }
    
    /**
     * Creates a short URL with optional base URL override.
     */
    public ShortenResponse createShortUrl(ShortenRequest request, String baseUrl) {
        log.debug("Creating short URL for: {}", request.getUrl());
        
        // Use provided baseUrl or fall back to configured shortDomain
        String effectiveDomain = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : shortDomain;
        
        // Fail-fast: Validate URL first (before any expensive operations)
        validateUrl(request.getUrl());
        validateExpiry(request.getExpiryInDays());
        
        String shortCode;
        
        // Check if customAlias is provided
        if (request.getCustomAlias() != null && !request.getCustomAlias().isEmpty()) {
            // Fail-fast: Validate customAlias format
            validateCustomAlias(request.getCustomAlias());
            
            shortCode = request.getCustomAlias();
            
            // Check if customAlias already exists (after format validation)
            if (urlRepository.existsByShortCode(shortCode)) {
                throw new AliasAlreadyExistsException(
                    "Custom alias '" + shortCode + "' is already in use"
                );
            }
        } else {
            // Auto-generate short code (only after URL validation passes)
            long id = tokenService.getNextId();
            shortCode = Base62Encoder.encode(id);
        }
        
        long now = System.currentTimeMillis() / 1000;
        
        // Calculate expiresAt if expiryInDays is provided
        Long expiresAt = null;
        if (request.getExpiryInDays() != null) {
            expiresAt = now + (request.getExpiryInDays() * 24L * 60L * 60L);
        }
        
        UrlMapping mapping = new UrlMapping(
            shortCode,
            request.getUrl(),
            now,
            "anonymous",
            true,
            expiresAt,
            request.getCustomAlias() != null,
            0L
        );
        
        urlRepository.save(mapping);
        
        log.info("Short URL created: {} -> {}", shortCode, request.getUrl());
        
        return new ShortenResponse(
            shortCode,
            effectiveDomain + "/" + shortCode,
            request.getUrl(),
            now,
            expiresAt
        );
    }
    
    /**
     * Validates expiry days constraint.
     * Fail-fast validation - throws exception immediately if invalid.
     */
    private void validateExpiry(Integer expiryInDays) {
        if (expiryInDays != null) {
            if (expiryInDays < 1 || expiryInDays > 365) {
                throw new InvalidUrlException("Expiry must be between 1 and 365 days");
            }
        }
    }
    
    /**
     * Validates URL format and constraints.
     * Fail-fast validation - throws exception immediately if invalid.
     */
    private void validateUrl(String url) {
        // Check 1: Null or empty
        if (url == null || url.trim().isEmpty()) {
            throw new InvalidUrlException("URL cannot be empty");
        }
        
        // Check 2: Length constraint
        if (url.length() > 2048) {
            throw new InvalidUrlException("URL exceeds maximum length of 2048 characters");
        }
        
        // Check 3: Format validation (must start with http:// or https://)
        if (!url.matches("^https?://.*")) {
            throw new InvalidUrlException("URL must start with http:// or https://");
        }
        
        // Check 4: Self-referencing URL (prevent shortening our own short URLs)
        if (effectiveDomain != null && url.toLowerCase().contains(effectiveDomain.toLowerCase().replace("https://", "").replace("http://", ""))) {
            throw new InvalidUrlException("Cannot shorten URLs from this domain");
        }
        
        // Check 5: Localhost and private IPs (security risk)
        if (url.matches("^https?://(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.).*")) {
            throw new InvalidUrlException("Cannot shorten localhost or private network URLs");
        }
    }
    
    /**
     * Validates custom alias format and constraints.
     * Fail-fast validation - throws exception immediately if invalid.
     */
    private void validateCustomAlias(String alias) {
        // Check 1: Length constraints
        if (alias.length() < 3) {
            throw new InvalidAliasException("Custom alias must be at least 3 characters");
        }
        if (alias.length() > 20) {
            throw new InvalidAliasException("Custom alias cannot exceed 20 characters");
        }
        
        // Check 2: Character validation (alphanumeric and hyphens only)
        if (!alias.matches("^[a-zA-Z0-9-]+$")) {
            throw new InvalidAliasException(
                "Custom alias can only contain letters, numbers, and hyphens"
            );
        }
        
        // Check 3: Reserved keywords (system endpoints)
        if (isReservedKeyword(alias)) {
            throw new InvalidAliasException(
                "Custom alias '" + alias + "' is a reserved keyword"
            );
        }
    }
    
    /**
     * Retrieves original URL for a given short code.
     * Validates that URL exists, is active, and not expired.
     */
    public UrlMapping getOriginalUrl(String shortCode) {
        log.debug("Retrieving URL for shortCode: {}", shortCode);
        
        UrlMapping urlMapping = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));
        
        if (!urlMapping.getIsActive()) {
            log.warn("Inactive URL accessed: {}", shortCode);
            throw new UrlExpiredException("This short URL has been deactivated");
        }
        
        if (urlMapping.getExpiresAt() != null && urlMapping.getExpiresAt() < System.currentTimeMillis() / 1000) {
            log.warn("Expired URL accessed: {}", shortCode);
            throw new UrlExpiredException("This short URL has expired");
        }
        
        log.info("Successfully retrieved URL for shortCode: {}", shortCode);
        return urlMapping;
    }
    
    /**
     * Checks if alias is a reserved system keyword.
     */
    private boolean isReservedKeyword(String alias) {
        String lowerAlias = alias.toLowerCase();
        return lowerAlias.equals("shorten") || 
               lowerAlias.equals("health") || 
               lowerAlias.equals("stats") || 
               lowerAlias.equals("api") || 
               lowerAlias.equals("admin");
    }
}
