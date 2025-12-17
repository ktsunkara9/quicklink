package inc.skt.quicklink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Health check response")
public class HealthResponse {

    @Schema(description = "Service status", example = "UP")
    private String status;

    @Schema(description = "Service name", example = "quicklink-url-shortener")
    private String service;

    @Schema(description = "Service version", example = "1.0.0")
    private String version;

    @Schema(description = "Current timestamp in seconds", example = "1704067200")
    private long timestamp;

    @Schema(description = "Dependency health checks")
    private Map<String, String> checks;

    public HealthResponse() {
    }

    public HealthResponse(String status, String service, String version, long timestamp, Map<String, String> checks) {
        this.status = status;
        this.service = service;
        this.version = version;
        this.timestamp = timestamp;
        this.checks = checks;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getChecks() {
        return checks;
    }

    public void setChecks(Map<String, String> checks) {
        this.checks = checks;
    }
}
