package inc.skt.quicklink.controller;


import inc.skt.quicklink.dto.ShortenRequest;
import inc.skt.quicklink.dto.ShortenResponse;
import inc.skt.quicklink.dto.UpdateUrlRequest;
import inc.skt.quicklink.dto.UrlStatsResponse;
import inc.skt.quicklink.exception.AliasAlreadyExistsException;
import inc.skt.quicklink.exception.InvalidAliasException;
import inc.skt.quicklink.exception.InvalidUrlException;
import inc.skt.quicklink.exception.UrlExpiredException;
import inc.skt.quicklink.exception.UrlNotFoundException;
import inc.skt.quicklink.model.UrlMapping;
import inc.skt.quicklink.service.AnalyticsService;
import inc.skt.quicklink.service.UrlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UrlController.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlService urlService;
    
    @MockBean
    private AnalyticsService analyticsService;

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
        when(urlService.createShortUrl(any(ShortenRequest.class), anyString())).thenReturn(response);

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
        when(urlService.createShortUrl(any(ShortenRequest.class), anyString())).thenReturn(response);

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
        when(urlService.createShortUrl(any(ShortenRequest.class), anyString())).thenReturn(response);

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
        when(urlService.createShortUrl(any(ShortenRequest.class), anyString()))
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
        when(urlService.createShortUrl(any(ShortenRequest.class), anyString()))
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
        when(urlService.createShortUrl(any(ShortenRequest.class), anyString()))
            .thenThrow(new AliasAlreadyExistsException("Custom alias 'mylink' is already in use"));

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Custom alias 'mylink' is already in use"))
            .andExpect(jsonPath("$.status").value(409));
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
        when(urlService.createShortUrl(any(ShortenRequest.class), anyString())).thenReturn(response);

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
        when(urlService.createShortUrl(any(ShortenRequest.class), anyString()))
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
        when(urlService.createShortUrl(any(ShortenRequest.class), anyString())).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    // ========== Redirect Endpoint Tests ==========

    @Test
    void should_return301Redirect_when_validShortCodeProvided() throws Exception {
        // Given
        UrlMapping urlMapping = new UrlMapping(
            "abc1234",
            "https://example.com/original",
            1704067200L,
            "anonymous",
            true,
            null,
            false,
            0L
        );
        when(urlService.getOriginalUrl("abc1234")).thenReturn(urlMapping);
        doNothing().when(analyticsService).recordClick(anyString(), anyString(), anyString());

        // When & Then
        mockMvc.perform(get("/abc1234"))
            .andExpect(status().isMovedPermanently())
            .andExpect(header().string("Location", "https://example.com/original"));
    }

    @Test
    void should_return404NotFound_when_shortCodeDoesNotExist() throws Exception {
        // Given
        when(urlService.getOriginalUrl("invalid"))
            .thenThrow(new UrlNotFoundException("Short URL not found: invalid"));

        // When & Then
        mockMvc.perform(get("/invalid"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Short URL not found: invalid"))
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void should_return410Gone_when_urlIsInactive() throws Exception {
        // Given
        when(urlService.getOriginalUrl("inactive"))
            .thenThrow(new UrlExpiredException("This short URL has been deactivated"));

        // When & Then
        mockMvc.perform(get("/inactive"))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value("This short URL has been deactivated"))
            .andExpect(jsonPath("$.status").value(410));
    }

    @Test
    void should_return410Gone_when_urlIsExpired() throws Exception {
        // Given
        when(urlService.getOriginalUrl("expired"))
            .thenThrow(new UrlExpiredException("This short URL has expired"));

        // When & Then
        mockMvc.perform(get("/expired"))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value("This short URL has expired"))
            .andExpect(jsonPath("$.status").value(410));
    }

    // ========== Stats Endpoint Tests ==========

    @Test
    void should_return200OK_when_statsRequestedForValidShortCode() throws Exception {
        // Given
        UrlStatsResponse response = new UrlStatsResponse(
            "abc1234",
            "https://example.com/test",
            42L,
            1704067200L,
            null,
            true
        );
        when(urlService.getUrlStats("abc1234")).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/stats/abc1234"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shortCode").value("abc1234"))
            .andExpect(jsonPath("$.longUrl").value("https://example.com/test"))
            .andExpect(jsonPath("$.clickCount").value(42))
            .andExpect(jsonPath("$.createdAt").value(1704067200L))
            .andExpect(jsonPath("$.expiresAt").doesNotExist())
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void should_return200OK_when_statsRequestedWithExpiry() throws Exception {
        // Given
        UrlStatsResponse response = new UrlStatsResponse(
            "abc1234",
            "https://example.com/test",
            10L,
            1704067200L,
            1706659200L,
            true
        );
        when(urlService.getUrlStats("abc1234")).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/stats/abc1234"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").value(1706659200L));
    }

    @Test
    void should_return200OK_when_statsRequestedForInactiveUrl() throws Exception {
        // Given
        UrlStatsResponse response = new UrlStatsResponse(
            "abc1234",
            "https://example.com/test",
            5L,
            1704067200L,
            null,
            false
        );
        when(urlService.getUrlStats("abc1234")).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/stats/abc1234"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void should_return404NotFound_when_statsRequestedForNonExistentShortCode() throws Exception {
        // Given
        when(urlService.getUrlStats("invalid"))
            .thenThrow(new UrlNotFoundException("Short URL not found: invalid"));

        // When & Then
        mockMvc.perform(get("/api/v1/stats/invalid"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Short URL not found: invalid"))
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void should_returnJsonResponse_when_statsRequested() throws Exception {
        // Given
        UrlStatsResponse response = new UrlStatsResponse(
            "abc1234",
            "https://example.com/test",
            0L,
            1704067200L,
            null,
            true
        );
        when(urlService.getUrlStats("abc1234")).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/stats/abc1234"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    // ========== PATCH Endpoint Tests ==========

    @Test
    void should_return200OK_when_expiryUpdatedSuccessfully() throws Exception {
        // Given
        UpdateUrlRequest request = new UpdateUrlRequest(30);
        ShortenResponse response = new ShortenResponse(
            "abc1234",
            "https://skt.inc/abc1234",
            "https://example.com/test",
            1704067200L,
            1706659200L
        );
        when(urlService.updateUrl(anyString(), any(UpdateUrlRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(patch("/api/v1/urls/abc1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shortCode").value("abc1234"))
            .andExpect(jsonPath("$.expiresAt").value(1706659200L));
    }

    @Test
    void should_return200OK_when_expiryRemovedWithNull() throws Exception {
        // Given
        UpdateUrlRequest request = new UpdateUrlRequest(null);
        ShortenResponse response = new ShortenResponse(
            "abc1234",
            "https://skt.inc/abc1234",
            "https://example.com/test",
            1704067200L,
            null
        );
        when(urlService.updateUrl(anyString(), any(UpdateUrlRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(patch("/api/v1/urls/abc1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void should_return404NotFound_when_patchCalledOnNonExistentUrl() throws Exception {
        // Given
        UpdateUrlRequest request = new UpdateUrlRequest(30);
        when(urlService.updateUrl(anyString(), any(UpdateUrlRequest.class)))
            .thenThrow(new UrlNotFoundException("Short URL not found: nonexistent"));

        // When & Then
        mockMvc.perform(patch("/api/v1/urls/nonexistent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Short URL not found: nonexistent"));
    }

    @Test
    void should_return400BadRequest_when_invalidExpiryProvided() throws Exception {
        // Given
        UpdateUrlRequest request = new UpdateUrlRequest(400);
        when(urlService.updateUrl(anyString(), any(UpdateUrlRequest.class)))
            .thenThrow(new InvalidUrlException("Expiry must be between 1 and 365 days"));

        // When & Then
        mockMvc.perform(patch("/api/v1/urls/abc1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Expiry must be between 1 and 365 days"));
    }

    // ========== DELETE Endpoint Tests ==========

    @Test
    void should_return204NoContent_when_urlDeletedSuccessfully() throws Exception {
        // Given
        doNothing().when(urlService).deleteUrl("abc1234");

        // When & Then
        mockMvc.perform(delete("/api/v1/urls/abc1234"))
            .andExpect(status().isNoContent());
    }

    @Test
    void should_return404NotFound_when_deleteCalledOnNonExistentUrl() throws Exception {
        // Given
        doThrow(new UrlNotFoundException("Short URL not found: nonexistent"))
            .when(urlService).deleteUrl("nonexistent");

        // When & Then
        mockMvc.perform(delete("/api/v1/urls/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Short URL not found: nonexistent"));
    }

    @Test
    void should_notReturnBody_when_deleteSuccessful() throws Exception {
        // Given
        doNothing().when(urlService).deleteUrl("abc1234");

        // When & Then
        mockMvc.perform(delete("/api/v1/urls/abc1234"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
    }
}
