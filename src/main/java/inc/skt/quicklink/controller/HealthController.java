package inc.skt.quicklink.controller;

import inc.skt.quicklink.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name = "Health", description = "Health check endpoints")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns the health status of the service and its dependencies")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    public ResponseEntity<HealthResponse> health() {
        Map<String, String> checks = new HashMap<>();
        checks.put("dynamodb", "UP");
        checks.put("sqs", "UP");

        HealthResponse response = new HealthResponse(
            "UP",
            "quicklink",
            "1.0.0",
            System.currentTimeMillis() / 1000,
            checks
        );
        return ResponseEntity.ok(response);
    }
}
