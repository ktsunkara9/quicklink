package inc.skt.quicklink.dto;

/**
 * Analytics event representing a URL click.
 * Sent to SQS for asynchronous processing.
 */
public class AnalyticsEvent {
    
    private String shortCode;
    private long timestamp;
    private String ipAddress;
    private String userAgent;
    
    public AnalyticsEvent() {
    }
    
    public AnalyticsEvent(String shortCode, long timestamp, String ipAddress, String userAgent) {
        this.shortCode = shortCode;
        this.timestamp = timestamp;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
    
    public String getShortCode() {
        return shortCode;
    }
    
    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    @Override
    public String toString() {
        return "AnalyticsEvent{" +
                "shortCode='" + shortCode + '\'' +
                ", timestamp=" + timestamp +
                ", ipAddress='" + ipAddress + '\'' +
                ", userAgent='" + userAgent + '\'' +
                '}';
    }
}
