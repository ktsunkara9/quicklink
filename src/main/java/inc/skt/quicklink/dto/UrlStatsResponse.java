package inc.skt.quicklink.dto;

/**
 * Response DTO for URL statistics endpoint.
 */
public class UrlStatsResponse {
    
    private String shortCode;
    private String longUrl;
    private Long clickCount;
    private Long createdAt;
    private Long expiresAt;
    private Boolean isActive;
    
    public UrlStatsResponse() {
    }
    
    public UrlStatsResponse(String shortCode, String longUrl, Long clickCount, Long createdAt, Long expiresAt, Boolean isActive) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.clickCount = clickCount;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.isActive = isActive;
    }
    
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
    
    public Long getClickCount() {
        return clickCount;
    }
    
    public void setClickCount(Long clickCount) {
        this.clickCount = clickCount;
    }
    
    public Long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
    
    public Long getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    @Override
    public String toString() {
        return "UrlStatsResponse{" +
                "shortCode='" + shortCode + '\'' +
                ", longUrl='" + longUrl + '\'' +
                ", clickCount=" + clickCount +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", isActive=" + isActive +
                '}';
    }
}
