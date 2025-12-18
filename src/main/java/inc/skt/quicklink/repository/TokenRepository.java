package inc.skt.quicklink.repository;

/**
 * Repository interface for token counter operations.
 * Provides atomic increment functionality for distributed ID generation.
 */
public interface TokenRepository {
    
    /**
     * Atomically increments the counter and returns the new value.
     * Uses DynamoDB's atomic ADD operation to ensure consistency across Lambda instances.
     * 
     * @param tokenId The counter identifier (e.g., "global_counter")
     * @param incrementBy Amount to increment (e.g., 100 for RANGE_SIZE)
     * @return New value after increment
     */
    long incrementAndGet(String tokenId, long incrementBy);
}
