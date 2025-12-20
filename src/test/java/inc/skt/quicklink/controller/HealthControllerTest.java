package inc.skt.quicklink.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ========== Successful Health Check Tests ==========

    @Test
    void should_return200OK_when_healthEndpointCalled() throws Exception {
        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk());
    }

    @Test
    void should_returnJsonResponse_when_healthEndpointCalled() throws Exception {
        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void should_returnCorrectHealthStatus_when_healthEndpointCalled() throws Exception {
        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("quicklink"))
            .andExpect(jsonPath("$.version").value("1.0.0"))
            .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    void should_returnDependencyChecks_when_healthEndpointCalled() throws Exception {
        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checks").exists())
            .andExpect(jsonPath("$.checks.dynamodb").value("UP"))
            .andExpect(jsonPath("$.checks.sqs").value("UP"));
    }

    @Test
    void should_returnAllRequiredFields_when_healthEndpointCalled() throws Exception {
        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.service").exists())
            .andExpect(jsonPath("$.version").exists())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.checks").exists());
    }

    // ========== HTTP Method Tests ==========

    @Test
    void should_return405MethodNotAllowed_when_postMethodUsed() throws Exception {
        // When & Then
        mockMvc.perform(post("/health"))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void should_return405MethodNotAllowed_when_putMethodUsed() throws Exception {
        // When & Then
        mockMvc.perform(put("/health"))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void should_return405MethodNotAllowed_when_deleteMethodUsed() throws Exception {
        // When & Then
        mockMvc.perform(delete("/health"))
            .andExpect(status().isMethodNotAllowed());
    }

    // ========== Edge Cases ==========

    @Test
    void should_returnValidTimestamp_when_healthEndpointCalled() throws Exception {
        // Given
        long beforeCall = System.currentTimeMillis() / 1000;

        // When & Then
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timestamp").isNumber())
            .andExpect(result -> {
                long timestamp = Long.parseLong(
                    result.getResponse().getContentAsString()
                        .split("\"timestamp\":")[1]
                        .split(",")[0]
                );
                long afterCall = System.currentTimeMillis() / 1000;
                assert timestamp >= beforeCall && timestamp <= afterCall;
            });
    }

    @Test
    void should_returnConsistentResponse_when_calledMultipleTimes() throws Exception {
        // When & Then - Call multiple times
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("quicklink"))
                .andExpect(jsonPath("$.version").value("1.0.0"));
        }
    }
}
