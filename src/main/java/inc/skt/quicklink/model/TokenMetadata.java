package inc.skt.quicklink.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Domain model representing token counter metadata in DynamoDB.
 * Maps to the 'quicklink-tokens' table.
 * Stores the global counter for distributed ID generation.
 */
@DynamoDbBean
public class TokenMetadata {
    
    private String tokenId;           // Primary Key - "global_counter"
    private Long currentRangeEnd;     // Last allocated ID
    private Long lastUpdated;         // Last allocation timestamp (seconds)
    
    public TokenMetadata() {
    }
    
    public TokenMetadata(String tokenId, Long currentRangeEnd, Long lastUpdated) {
        this.tokenId = tokenId;
        this.currentRangeEnd = currentRangeEnd;
        this.lastUpdated = lastUpdated;
    }
    
    @DynamoDbPartitionKey
    public String getTokenId() {
        return tokenId;
    }
    
    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }
    
    public Long getCurrentRangeEnd() {
        return currentRangeEnd;
    }
    
    public void setCurrentRangeEnd(Long currentRangeEnd) {
        this.currentRangeEnd = currentRangeEnd;
    }
    
    public Long getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(Long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
