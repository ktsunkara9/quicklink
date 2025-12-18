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
     */
    public ShortenResponse createShortUrl(ShortenRequest request) {
        long id = tokenService.getNextId();
        String shortCode = Base62Encoder.encode(id);
        long now = System.currentTimeMillis() / 1000;
        
        UrlMapping mapping = new UrlMapping(
            shortCode,
            request.url(),
            now,
            "anonymous",
            true,
            null,
            request.customAlias() != null,
            0L
        );
        
        urlRepository.save(mapping);
        
        return new ShortenResponse(
            shortCode,
            shortDomain + "/" + shortCode,
            request.url(),
            now
        );
    }
}
