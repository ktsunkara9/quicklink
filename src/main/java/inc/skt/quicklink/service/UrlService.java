package inc.skt.quicklink.service;

import inc.skt.quicklink.dto.ShortenRequest;
import inc.skt.quicklink.dto.ShortenResponse;
import inc.skt.quicklink.model.UrlMapping;
import inc.skt.quicklink.repository.UrlRepository;
import inc.skt.quicklink.util.Base62Encoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service layer for URL shortening business logic.
 * Handles URL creation, retrieval, and validation.
 */
@Service
public class UrlService {
    
    private final UrlRepository urlRepository;
    private final TokenService tokenService;
    
    @Value("${app.short-domain:https://skt.inc}")
    private String shortDomain;
    
    public UrlService(UrlRepository urlRepository, TokenService tokenService) {
        this.urlRepository = urlRepository;
        this.tokenService = tokenService;
    }
    
    /**
     * Creates a short URL from a long URL.
     * If customAlias is provided, uses it as the short code.
     * Otherwise, auto-generates a short code using TokenService.
     */
    public ShortenResponse createShortUrl(ShortenRequest request) {
        String shortCode;
        
        // Check if customAlias is provided
        if (request.getCustomAlias() != null && !request.getCustomAlias().isEmpty()) {
            shortCode = request.getCustomAlias();
            
            // Check if customAlias already exists
            if (urlRepository.existsByShortCode(shortCode)) {
                throw new IllegalArgumentException(
                    "Custom alias '" + shortCode + "' is already in use"
                );
            }
        } else {
            // Auto-generate short code
            long id = tokenService.getNextId();
            shortCode = Base62Encoder.encode(id);
        }
        
        long now = System.currentTimeMillis() / 1000;
        
        UrlMapping mapping = new UrlMapping(
            shortCode,
            request.getUrl(),
            now,
            "anonymous",
            true,
            null,
            request.getCustomAlias() != null,
            0L
        );
        
        urlRepository.save(mapping);
        
        return new ShortenResponse(
            shortCode,
            shortDomain + "/" + shortCode,
            request.getUrl(),
            now
        );
    }
}
