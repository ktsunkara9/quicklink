package inc.skt.quicklink.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Domain model representing a URL mapping entry in DynamoDB.
 * Maps to the 'quicklink-urls' table.
 */
@DynamoDbBean
public class UrlMapping {
    
    private String shortCode;      // Primary Key - 7-char base62 code
    private String longUrl;        // Original URL
    private Long createdAt;        // Unix timestamp (seconds)
    private String userId;         // Creator identifier or "anonymous"
    private Boolean isActive;      // Soft delete flag
    private Long expiresAt;        // Optional: Custom expiry (TTL)
    private Boolean customAlias;   // User-chosen vs auto-generated
    private Long clickCount;       // Denormalized click counter
    
    public UrlMapping() {
    }
    
    public UrlMapping(String shortCode, String longUrl, Long createdAt, String userId, 
                      Boolean isActive, Long expiresAt, Boolean customAlias, Long clickCount) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.userId = userId;
        this.isActive = isActive;
        this.expiresAt = expiresAt;
        this.customAlias = customAlias;
        this.clickCount = clickCount;
    }
    
    @DynamoDbPartitionKey
    public String getShortCode() {
        return shortCode;
    }
    
    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }
    
    public String getLongUrl() {
        return longUrl;
    }
    
    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }
    
    public Long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public Long getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public Boolean getCustomAlias() {
        return customAlias;
    }
    
    public void setCustomAlias(Boolean customAlias) {
        this.customAlias = customAlias;
    }
    
    public Long getClickCount() {
        return clickCount;
    }
    
    public void setClickCount(Long clickCount) {
        this.clickCount = clickCount;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UrlMapping that = (UrlMapping) o;
        return shortCode != null ? shortCode.equals(that.shortCode) : that.shortCode == null;
    }
    
    @Override
    public int hashCode() {
        return shortCode != null ? shortCode.hashCode() : 0;
    }
    
    @Override
    public String toString() {
        return "UrlMapping{" +
                "shortCode='" + shortCode + '\'' +
                ", longUrl='" + longUrl + '\'' +
                ", createdAt=" + createdAt +
                ", userId='" + userId + '\'' +
                ", isActive=" + isActive +
                ", expiresAt=" + expiresAt +
                ", customAlias=" + customAlias +
                ", clickCount=" + clickCount +
                '}';
    }
}
