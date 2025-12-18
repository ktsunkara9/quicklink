package inc.skt.quicklink.service;

import inc.skt.quicklink.repository.TokenRepository;
import org.springframework.stereotype.Service;

/**
 * Token service for distributed ID generation.
 * Allocates ranges of IDs from DynamoDB and caches them in memory.
 * Uses range-based allocation (100 IDs at a time) to minimize DynamoDB calls.
 */
@Service
public class TokenService {
    
    private static final int RANGE_SIZE = 100;
    private static final String TOKEN_ID = "global_counter";
    
    private final TokenRepository tokenRepository;
    private long currentId = 0;
    private long rangeEnd = 0;
    
    public TokenService(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }
    
    /**
     * Gets the next unique ID.
     * Allocates a new range from DynamoDB if current range is exhausted.
     * Thread-safe for concurrent access.
     * 
     * @return Next unique ID
     */
    public synchronized long getNextId() {
        // Check if current range is exhausted
        if (currentId >= rangeEnd) {
            allocateNewRange();
        }
        
        return currentId++;
    }
    
    /**
     * Allocates a new range of IDs from DynamoDB.
     * Uses atomic ADD operation to increment the counter.
     */
    private void allocateNewRange() {
        long newRangeEnd = tokenRepository.incrementAndGet(TOKEN_ID, RANGE_SIZE);
        currentId = newRangeEnd - RANGE_SIZE;
        rangeEnd = newRangeEnd;
    }
}
