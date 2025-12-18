package inc.skt.quicklink.repository;

import inc.skt.quicklink.model.UrlMapping;
import java.util.Optional;

/**
 * Repository interface for URL mapping operations.
 * Provides abstraction over storage implementation (in-memory, DynamoDB, etc.)
 */
public interface UrlRepository {
    
    /**
     * Save a URL mapping.
     * 
     * @param urlMapping URL mapping to save
     * @return Saved URL mapping
     */
    UrlMapping save(UrlMapping urlMapping);
    
    /**
     * Find a URL mapping by short code.
     * 
     * @param shortCode Short code to search for
     * @return Optional containing the URL mapping if found, empty otherwise
     */
    Optional<UrlMapping> findByShortCode(String shortCode);
    
    /**
     * Check if a short code already exists.
     * 
     * @param shortCode Short code to check
     * @return true if exists, false otherwise
     */
    boolean existsByShortCode(String shortCode);
    
    /**
     * Delete a URL mapping by short code.
     * 
     * @param shortCode Short code of the mapping to delete
     */
    void deleteByShortCode(String shortCode);
}
