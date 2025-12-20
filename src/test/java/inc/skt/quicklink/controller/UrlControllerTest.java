package inc.skt.quicklink.controller;

import inc.skt.quicklink.dto.ShortenRequest;
import inc.skt.quicklink.dto.ShortenResponse;
import inc.skt.quicklink.exception.AliasAlreadyExistsException;
import inc.skt.quicklink.exception.InvalidAliasException;
import inc.skt.quicklink.exception.InvalidUrlException;
import inc.skt.quicklink.service.UrlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlService urlService;

    // ========== Successful Request Tests ==========

    @Test
    void should_return201Created_when_validUrlProvided() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com/long-url", null);
        ShortenResponse response = new ShortenResponse(
            "0000001",
            "https://skt.inc/0000001",
            "https://example.com/long-url",
            1704067200L,
            null
        );
        when(urlService.createShortUrl(any(ShortenRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shortCode").value("0000001"))
            .andExpect(jsonPath("$.shortUrl").value("https://skt.inc/0000001"))
            .andExpect(jsonPath("$.longUrl").value("https://example.com/long-url"))
            .andExpect(jsonPath("$.createdAt").value(1704067200L))
            .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void should_return201Created_when_customAliasProvided() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com/long-url", "mylink");
        ShortenResponse response = new ShortenResponse(
            "mylink",
            "https://skt.inc/mylink",
            "https://example.com/long-url",
            1704067200L,
            null
        );
        when(urlService.createShortUrl(any(ShortenRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shortCode").value("mylink"))
            .andExpect(jsonPath("$.shortUrl").value("https://skt.inc/mylink"));
    }

    @Test
    void should_return201Created_when_expiryProvided() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com/long-url", null, 30);
        ShortenResponse response = new ShortenResponse(
            "0000001",
            "https://skt.inc/0000001",
            "https://example.com/long-url",
            1704067200L,
            1706659200L
        );
        when(urlService.createShortUrl(any(ShortenRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expiresAt").value(1706659200L));
    }

    // ========== Validation Error Tests ==========

    @Test
    void should_return400BadRequest_when_urlIsInvalid() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest("", null);
        when(urlService.createShortUrl(any(ShortenRequest.class)))
            .thenThrow(new InvalidUrlException("URL cannot be empty"));

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("URL cannot be empty"))
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_return400BadRequest_when_aliasIsInvalid() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", "ab");
        when(urlService.createShortUrl(any(ShortenRequest.class)))
            .thenThrow(new InvalidAliasException("Custom alias must be at least 3 characters"));

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Custom alias must be at least 3 characters"))
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_return409Conflict_when_aliasAlreadyExists() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", "mylink");
        when(urlService.createShortUrl(any(ShortenRequest.class)))
            .thenThrow(new AliasAlreadyExistsException("Custom alias 'mylink' is already in use"));

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Custom alias 'mylink' is already in use"))
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void should_return400BadRequest_when_malformedJson() throws Exception {
        // Given
        String malformedJson = "{\"url\": \"https://example.com\", invalid}";

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Malformed JSON request"))
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_return400BadRequest_when_missingContentType() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", null);

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void should_return400BadRequest_when_emptyRequestBody() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // ========== Edge Cases ==========

    @Test
    void should_return201Created_when_allFieldsProvided() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com/long-url", "mylink", 30);
        ShortenResponse response = new ShortenResponse(
            "mylink",
            "https://skt.inc/mylink",
            "https://example.com/long-url",
            1704067200L,
            1706659200L
        );
        when(urlService.createShortUrl(any(ShortenRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shortCode").value("mylink"))
            .andExpect(jsonPath("$.expiresAt").value(1706659200L));
    }

    @Test
    void should_return500InternalServerError_when_unexpectedErrorOccurs() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", null);
        when(urlService.createShortUrl(any(ShortenRequest.class)))
            .thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Internal server error"))
            .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    void should_returnJsonResponse_when_validRequest() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", null);
        ShortenResponse response = new ShortenResponse(
            "0000001",
            "https://skt.inc/0000001",
            "https://example.com",
            1704067200L,
            null
        );
        when(urlService.createShortUrl(any(ShortenRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
