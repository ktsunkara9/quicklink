package inc.skt.quicklink.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to update URL properties")
public class UpdateUrlRequest {
    
    @Schema(description = "New expiry in days (1-365, null to remove expiry)", example = "60")
    private Integer expiryInDays;
    
    public UpdateUrlRequest() {
    }
    
    public UpdateUrlRequest(Integer expiryInDays) {
        this.expiryInDays = expiryInDays;
    }
    
    public Integer getExpiryInDays() {
        return expiryInDays;
    }
    
    public void setExpiryInDays(Integer expiryInDays) {
        this.expiryInDays = expiryInDays;
    }
}
