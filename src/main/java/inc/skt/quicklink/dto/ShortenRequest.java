package inc.skt.quicklink.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to shorten a URL")
public class ShortenRequest {
    
    @Schema(description = "URL to shorten", example = "https://example.com/very/long/url", required = true)
    private String url;
    
    @Schema(description = "Optional custom alias for the short code", example = "mylink")
    private String customAlias;
    
    public ShortenRequest() {
    }
    
    public ShortenRequest(String url, String customAlias) {
        this.url = url;
        this.customAlias = customAlias;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getCustomAlias() {
        return customAlias;
    }
    
    public void setCustomAlias(String customAlias) {
        this.customAlias = customAlias;
    }
}
