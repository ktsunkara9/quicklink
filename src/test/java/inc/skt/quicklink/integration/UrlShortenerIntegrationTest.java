package inc.skt.quicklink.integration;

import inc.skt.quicklink.dto.ShortenRequest;
import inc.skt.quicklink.dto.ShortenResponse;
import inc.skt.quicklink.dto.UpdateUrlRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for URL Shortener application.
 * Tests full stack with in-memory repositories (no external dependencies).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrlShortenerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_createShortUrl_andRedirect_endToEnd() throws Exception {
        // Create short URL
        ShortenRequest request = new ShortenRequest("https://example.com/test", null);
        
        MvcResult createResult = mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shortCode").exists())
            .andExpect(jsonPath("$.longUrl").value("https://example.com/test"))
            .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        ShortenResponse response = objectMapper.readValue(responseJson, ShortenResponse.class);
        String shortCode = response.getShortCode();

        // Redirect using short code
        mockMvc.perform(get("/" + shortCode))
            .andExpect(status().isMovedPermanently())
            .andExpect(header().string("Location", "https://example.com/test"));
    }

    @Test
    void should_createShortUrl_getStats_andVerifyClickCount() throws Exception {
        // Create short URL
        ShortenRequest request = new ShortenRequest("https://example.com/stats-test", null);
        
        MvcResult createResult = mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        ShortenResponse response = objectMapper.readValue(responseJson, ShortenResponse.class);
        String shortCode = response.getShortCode();

        // Get stats (should have 0 clicks)
        mockMvc.perform(get("/api/v1/stats/" + shortCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clickCount").value(0));

        // Click the URL
        mockMvc.perform(get("/" + shortCode))
            .andExpect(status().isMovedPermanently());

        // Get stats again (should have 1 click)
        mockMvc.perform(get("/api/v1/stats/" + shortCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clickCount").value(1));
    }

    @Test
    void should_createShortUrl_updateExpiry_andVerify() throws Exception {
        // Create short URL without expiry
        ShortenRequest request = new ShortenRequest("https://example.com/update-test", null);
        
        MvcResult createResult = mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expiresAt").doesNotExist())
            .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        ShortenResponse response = objectMapper.readValue(responseJson, ShortenResponse.class);
        String shortCode = response.getShortCode();

        // Update expiry
        UpdateUrlRequest updateRequest = new UpdateUrlRequest(30);
        mockMvc.perform(patch("/api/v1/urls/" + shortCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").exists());

        // Verify stats show expiry
        mockMvc.perform(get("/api/v1/stats/" + shortCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void should_createShortUrl_softDelete_andFailRedirect() throws Exception {
        // Create short URL
        ShortenRequest request = new ShortenRequest("https://example.com/delete-test", null);
        
        MvcResult createResult = mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        ShortenResponse response = objectMapper.readValue(responseJson, ShortenResponse.class);
        String shortCode = response.getShortCode();

        // Verify redirect works
        mockMvc.perform(get("/" + shortCode))
            .andExpect(status().isMovedPermanently());

        // Soft delete
        mockMvc.perform(delete("/api/v1/urls/" + shortCode))
            .andExpect(status().isNoContent());

        // Verify redirect fails with 410 Gone
        mockMvc.perform(get("/" + shortCode))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value("This short URL has been deactivated"));

        // Verify stats show inactive
        mockMvc.perform(get("/api/v1/stats/" + shortCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void should_createCustomAlias_andRedirect() throws Exception {
        // Create short URL with custom alias
        ShortenRequest request = new ShortenRequest("https://example.com/custom", "myalias");
        
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shortCode").value("myalias"));

        // Redirect using custom alias
        mockMvc.perform(get("/myalias"))
            .andExpect(status().isMovedPermanently())
            .andExpect(header().string("Location", "https://example.com/custom"));
    }

    @Test
    void should_preventDuplicateCustomAlias() throws Exception {
        // Create first URL with custom alias
        ShortenRequest request1 = new ShortenRequest("https://example.com/first", "duplicate");
        
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());

        // Try to create second URL with same alias
        ShortenRequest request2 = new ShortenRequest("https://example.com/second", "duplicate");
        
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Custom alias 'duplicate' is already in use"));
    }

    @Test
    void should_createUrlWithExpiry_andVerifyInStats() throws Exception {
        // Create short URL with expiry
        ShortenRequest request = new ShortenRequest("https://example.com/expiry-test", null, 60);
        
        MvcResult createResult = mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expiresAt").exists())
            .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        ShortenResponse response = objectMapper.readValue(responseJson, ShortenResponse.class);
        String shortCode = response.getShortCode();

        // Verify stats show expiry
        mockMvc.perform(get("/api/v1/stats/" + shortCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").value(response.getExpiresAt()));
    }

    @Test
    void should_handleMultipleClicks_andIncrementCount() throws Exception {
        // Create short URL
        ShortenRequest request = new ShortenRequest("https://example.com/multi-click", null);
        
        MvcResult createResult = mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        ShortenResponse response = objectMapper.readValue(responseJson, ShortenResponse.class);
        String shortCode = response.getShortCode();

        // Click 3 times
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isMovedPermanently());
        }

        // Verify click count is 3
        mockMvc.perform(get("/api/v1/stats/" + shortCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clickCount").value(3));
    }

    @Test
    void should_healthCheck_returnUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("quicklink"));
    }

    @Test
    void should_removeExpiry_withNullValue() throws Exception {
        // Create short URL with expiry
        ShortenRequest request = new ShortenRequest("https://example.com/remove-expiry", null, 30);
        
        MvcResult createResult = mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expiresAt").exists())
            .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        ShortenResponse response = objectMapper.readValue(responseJson, ShortenResponse.class);
        String shortCode = response.getShortCode();

        // Remove expiry
        UpdateUrlRequest updateRequest = new UpdateUrlRequest(null);
        mockMvc.perform(patch("/api/v1/urls/" + shortCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").doesNotExist());

        // Verify stats show no expiry
        mockMvc.perform(get("/api/v1/stats/" + shortCode))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }
}
