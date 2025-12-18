package inc.skt.quicklink.repository;

import inc.skt.quicklink.model.UrlMapping;
import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of UrlRepository using ConcurrentHashMap.
 * Used for local development and testing before DynamoDB integration.
 * Thread-safe for concurrent access.
 */
@Repository
public class InMemoryUrlRepository implements UrlRepository {
    
    private final Map<String, UrlMapping> storage = new ConcurrentHashMap<>();
    
    @Override
    public UrlMapping save(UrlMapping urlMapping) {
        storage.put(urlMapping.getShortCode(), urlMapping);
        return urlMapping;
    }
    
    @Override
    public Optional<UrlMapping> findByShortCode(String shortCode) {
        return Optional.ofNullable(storage.get(shortCode));
    }
    
    @Override
    public boolean existsByShortCode(String shortCode) {
        return storage.containsKey(shortCode);
    }
    
    @Override
    public void deleteByShortCode(String shortCode) {
        storage.remove(shortCode);
    }
}
