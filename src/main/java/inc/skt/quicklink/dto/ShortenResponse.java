package inc.skt.quicklink.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response after shortening a URL")
public class ShortenResponse {
    
    @Schema(description = "Generated short code", example = "aB3xY9z")
    private String shortCode;
    
    @Schema(description = "Complete short URL", example = "https://short.link/aB3xY9z")
    private String shortUrl;
    
    @Schema(description = "Original long URL", example = "https://example.com/very/long/url")
    private String longUrl;
    
    @Schema(description = "Creation timestamp in seconds", example = "1704067200")
    private Long createdAt;
    
    @Schema(description = "Expiry timestamp in seconds (null if never expires)", example = "1735689600")
    private Long expiresAt;
    
    public ShortenResponse() {
    }
    
    public ShortenResponse(String shortCode, String shortUrl, String longUrl, Long createdAt) {
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
    }
    
    public ShortenResponse(String shortCode, String shortUrl, String longUrl, Long createdAt, Long expiresAt) {
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
    
    public String getShortCode() {
        return shortCode;
    }
    
    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }
    
    public String getShortUrl() {
        return shortUrl;
    }
    
    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
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
    
    public Long getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
