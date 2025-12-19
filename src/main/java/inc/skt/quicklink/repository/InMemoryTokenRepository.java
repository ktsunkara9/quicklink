package inc.skt.quicklink.repository;

import org.springframework.stereotype.Repository;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of TokenRepository using AtomicLong.
 * Used for local development and testing before DynamoDB integration.
 * Thread-safe for concurrent access.
 */
@Repository
public class InMemoryTokenRepository implements TokenRepository {
    
    private final AtomicLong counter = new AtomicLong(0);
    
    @Override
    public long incrementAndGet(String tokenId, long incrementBy) {
        return counter.addAndGet(incrementBy);
    }
}
