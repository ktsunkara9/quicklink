package inc.skt.quicklink.service;

import inc.skt.quicklink.dto.ShortenRequest;
import inc.skt.quicklink.dto.ShortenResponse;
import inc.skt.quicklink.dto.UpdateUrlRequest;
import inc.skt.quicklink.dto.UrlStatsResponse;
import inc.skt.quicklink.exception.AliasAlreadyExistsException;
import inc.skt.quicklink.exception.InvalidAliasException;
import inc.skt.quicklink.exception.InvalidUrlException;
import inc.skt.quicklink.exception.UrlNotFoundException;
import inc.skt.quicklink.model.UrlMapping;
import inc.skt.quicklink.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "shortDomain", "https://skt.inc");
    }

    // ========== Successful URL Creation Tests ==========

    @Test
    void should_createShortUrl_when_validUrlProvided() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com/long-url", null);
        when(tokenService.getNextId()).thenReturn(1L);
        when(urlRepository.save(any(UrlMapping.class))).thenReturn(null);

        // When
        ShortenResponse response = urlService.createShortUrl(request);

        // Then
        assertNotNull(response);
        assertEquals("0000001", response.getShortCode());
        assertEquals("https://skt.inc/0000001", response.getShortUrl());
        assertEquals("https://example.com/long-url", response.getLongUrl());
        assertNotNull(response.getCreatedAt());
        assertNull(response.getExpiresAt());
        verify(tokenService, times(1)).getNextId();
        verify(urlRepository, times(1)).save(any(UrlMapping.class));
    }

    @Test
    void should_createShortUrl_when_validCustomAliasProvided() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com/long-url", "mylink");
        when(urlRepository.existsByShortCode("mylink")).thenReturn(false);
        when(urlRepository.save(any(UrlMapping.class))).thenReturn(null);

        // When
        ShortenResponse response = urlService.createShortUrl(request);

        // Then
        assertNotNull(response);
        assertEquals("mylink", response.getShortCode());
        assertEquals("https://skt.inc/mylink", response.getShortUrl());
        verify(urlRepository, times(1)).existsByShortCode("mylink");
        verify(urlRepository, times(1)).save(any(UrlMapping.class));
        verify(tokenService, never()).getNextId();
    }

    @Test
    void should_createShortUrl_when_validExpiryProvided() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com/long-url", null, 30);
        when(tokenService.getNextId()).thenReturn(1L);
        when(urlRepository.save(any(UrlMapping.class))).thenReturn(null);

        // When
        ShortenResponse response = urlService.createShortUrl(request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getExpiresAt());
        assertTrue(response.getExpiresAt() > response.getCreatedAt());
        verify(urlRepository, times(1)).save(any(UrlMapping.class));
    }

    // ========== URL Validation Tests ==========

    @Test
    void should_throwInvalidUrlException_when_urlIsNull() {
        // Given
        ShortenRequest request = new ShortenRequest(null, null);

        // When & Then
        InvalidUrlException exception = assertThrows(InvalidUrlException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("URL cannot be empty", exception.getMessage());
        verify(urlRepository, never()).save(any(UrlMapping.class));
    }

    @Test
    void should_throwInvalidUrlException_when_urlIsEmpty() {
        // Given
        ShortenRequest request = new ShortenRequest("", null);

        // When & Then
        InvalidUrlException exception = assertThrows(InvalidUrlException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("URL cannot be empty", exception.getMessage());
    }

    @Test
    void should_throwInvalidUrlException_when_urlExceedsMaxLength() {
        // Given
        String longUrl = "https://example.com/" + "a".repeat(2050);
        ShortenRequest request = new ShortenRequest(longUrl, null);

        // When & Then
        InvalidUrlException exception = assertThrows(InvalidUrlException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("URL exceeds maximum length of 2048 characters", exception.getMessage());
    }

    @Test
    void should_throwInvalidUrlException_when_urlDoesNotStartWithHttpOrHttps() {
        // Given
        ShortenRequest request = new ShortenRequest("ftp://example.com", null);

        // When & Then
        InvalidUrlException exception = assertThrows(InvalidUrlException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("URL must start with http:// or https://", exception.getMessage());
    }

    @Test
    void should_throwInvalidUrlException_when_urlIsSelfReferencing() {
        // Given
        ShortenRequest request = new ShortenRequest("https://skt.inc/abc123", null);

        // When & Then
        InvalidUrlException exception = assertThrows(InvalidUrlException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("Cannot shorten URLs from this domain", exception.getMessage());
    }

    @Test
    void should_throwInvalidUrlException_when_urlIsLocalhost() {
        // Given
        ShortenRequest request = new ShortenRequest("http://localhost:8080/test", null);

        // When & Then
        InvalidUrlException exception = assertThrows(InvalidUrlException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("Cannot shorten localhost or private network URLs", exception.getMessage());
    }

    @Test
    void should_throwInvalidUrlException_when_urlIsPrivateIP() {
        // Given
        ShortenRequest request = new ShortenRequest("http://192.168.1.1/test", null);

        // When & Then
        InvalidUrlException exception = assertThrows(InvalidUrlException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("Cannot shorten localhost or private network URLs", exception.getMessage());
    }

    // ========== Custom Alias Validation Tests ==========

    @Test
    void should_throwInvalidAliasException_when_aliasTooShort() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", "ab");

        // When & Then
        InvalidAliasException exception = assertThrows(InvalidAliasException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("Custom alias must be at least 3 characters", exception.getMessage());
    }

    @Test
    void should_throwInvalidAliasException_when_aliasTooLong() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", "a".repeat(21));

        // When & Then
        InvalidAliasException exception = assertThrows(InvalidAliasException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("Custom alias cannot exceed 20 characters", exception.getMessage());
    }

    @Test
    void should_throwInvalidAliasException_when_aliasContainsInvalidCharacters() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", "my link");

        // When & Then
        InvalidAliasException exception = assertThrows(InvalidAliasException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("Custom alias can only contain letters, numbers, and hyphens", exception.getMessage());
    }

    @Test
    void should_throwInvalidAliasException_when_aliasIsReservedKeyword() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", "shorten");

        // When & Then
        InvalidAliasException exception = assertThrows(InvalidAliasException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("Custom alias 'shorten' is a reserved keyword", exception.getMessage());
    }

    @Test
    void should_throwAliasAlreadyExistsException_when_aliasAlreadyExists() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", "mylink");
        when(urlRepository.existsByShortCode("mylink")).thenReturn(true);

        // When & Then
        AliasAlreadyExistsException exception = assertThrows(AliasAlreadyExistsException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("Custom alias 'mylink' is already in use", exception.getMessage());
        verify(urlRepository, never()).save(any(UrlMapping.class));
    }

    // ========== Expiry Validation Tests ==========

    @Test
    void should_throwInvalidUrlException_when_expiryIsZero() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", null, 0);

        // When & Then
        InvalidUrlException exception = assertThrows(InvalidUrlException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("Expiry must be between 1 and 365 days", exception.getMessage());
    }

    @Test
    void should_throwInvalidUrlException_when_expiryExceedsMaximum() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", null, 366);

        // When & Then
        InvalidUrlException exception = assertThrows(InvalidUrlException.class, 
            () -> urlService.createShortUrl(request));
        assertEquals("Expiry must be between 1 and 365 days", exception.getMessage());
    }

    @Test
    void should_createShortUrl_when_expiryIsMinimum() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", null, 1);
        when(tokenService.getNextId()).thenReturn(1L);
        when(urlRepository.save(any(UrlMapping.class))).thenReturn(null);

        // When
        ShortenResponse response = urlService.createShortUrl(request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getExpiresAt());
    }

    @Test
    void should_createShortUrl_when_expiryIsMaximum() {
        // Given
        ShortenRequest request = new ShortenRequest("https://example.com", null, 365);
        when(tokenService.getNextId()).thenReturn(1L);
        when(urlRepository.save(any(UrlMapping.class))).thenReturn(null);

        // When
        ShortenResponse response = urlService.createShortUrl(request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getExpiresAt());
    }

    // ========== Get URL Stats Tests ==========

    @Test
    void should_returnUrlStats_when_validShortCodeProvided() {
        // Given
        UrlMapping mapping = new UrlMapping(
            "abc123",
            "https://example.com/test",
            1704067200L,
            "anonymous",
            true,
            null,
            false,
            42L
        );
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(mapping));

        // When
        UrlStatsResponse response = urlService.getUrlStats("abc123");

        // Then
        assertNotNull(response);
        assertEquals("abc123", response.getShortCode());
        assertEquals("https://example.com/test", response.getLongUrl());
        assertEquals(42L, response.getClickCount());
        assertEquals(1704067200L, response.getCreatedAt());
        assertNull(response.getExpiresAt());
        assertTrue(response.getIsActive());
        verify(urlRepository, times(1)).findByShortCode("abc123");
    }

    @Test
    void should_returnUrlStatsWithExpiry_when_urlHasExpiry() {
        // Given
        UrlMapping mapping = new UrlMapping(
            "abc123",
            "https://example.com/test",
            1704067200L,
            "anonymous",
            true,
            1706659200L,
            false,
            10L
        );
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(mapping));

        // When
        UrlStatsResponse response = urlService.getUrlStats("abc123");

        // Then
        assertNotNull(response);
        assertEquals(1706659200L, response.getExpiresAt());
    }

    @Test
    void should_returnUrlStatsWithZeroClicks_when_urlHasNoClicks() {
        // Given
        UrlMapping mapping = new UrlMapping(
            "abc123",
            "https://example.com/test",
            1704067200L,
            "anonymous",
            true,
            null,
            false,
            0L
        );
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(mapping));

        // When
        UrlStatsResponse response = urlService.getUrlStats("abc123");

        // Then
        assertNotNull(response);
        assertEquals(0L, response.getClickCount());
    }

    @Test
    void should_returnUrlStatsForInactiveUrl_when_urlIsInactive() {
        // Given
        UrlMapping mapping = new UrlMapping(
            "abc123",
            "https://example.com/test",
            1704067200L,
            "anonymous",
            false,
            null,
            false,
            5L
        );
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(mapping));

        // When
        UrlStatsResponse response = urlService.getUrlStats("abc123");

        // Then
        assertNotNull(response);
        assertFalse(response.getIsActive());
        assertEquals(5L, response.getClickCount());
    }

    @Test
    void should_throwUrlNotFoundException_when_shortCodeDoesNotExist() {
        // Given
        when(urlRepository.findByShortCode("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        UrlNotFoundException exception = assertThrows(UrlNotFoundException.class,
            () -> urlService.getUrlStats("nonexistent"));
        assertEquals("Short URL not found: nonexistent", exception.getMessage());
        verify(urlRepository, times(1)).findByShortCode("nonexistent");
    }

    // ========== Update URL Tests ==========

    @Test
    void should_updateExpiry_when_validExpiryProvided() {
        // Given
        UpdateUrlRequest request = new UpdateUrlRequest(30);
        UrlMapping existing = new UrlMapping(
            "abc123",
            "https://example.com/test",
            1704067200L,
            "anonymous",
            true,
            null,
            false,
            10L
        );
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(existing));
        doNothing().when(urlRepository).updateExpiry(anyString(), any());

        // When
        ShortenResponse response = urlService.updateUrl("abc123", request);

        // Then
        assertNotNull(response);
        assertEquals("abc123", response.getShortCode());
        assertNotNull(response.getExpiresAt());
        verify(urlRepository, times(1)).updateExpiry(eq("abc123"), any());
    }

    @Test
    void should_removeExpiry_when_nullExpiryProvided() {
        // Given
        UpdateUrlRequest request = new UpdateUrlRequest(null);
        UrlMapping existing = new UrlMapping(
            "abc123",
            "https://example.com/test",
            1704067200L,
            "anonymous",
            true,
            1706659200L,
            false,
            10L
        );
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(existing));
        doNothing().when(urlRepository).updateExpiry(anyString(), any());

        // When
        ShortenResponse response = urlService.updateUrl("abc123", request);

        // Then
        assertNotNull(response);
        assertNull(response.getExpiresAt());
        verify(urlRepository, times(1)).updateExpiry("abc123", null);
    }

    @Test
    void should_throwUrlNotFoundException_when_updateCalledOnNonExistentUrl() {
        // Given
        UpdateUrlRequest request = new UpdateUrlRequest(30);
        when(urlRepository.findByShortCode("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        UrlNotFoundException exception = assertThrows(UrlNotFoundException.class,
            () -> urlService.updateUrl("nonexistent", request));
        assertEquals("Short URL not found: nonexistent", exception.getMessage());
        verify(urlRepository, never()).updateExpiry(anyString(), any());
    }

    @Test
    void should_throwInvalidUrlException_when_updateWithInvalidExpiry() {
        // Given
        UpdateUrlRequest request = new UpdateUrlRequest(400);

        // When & Then
        InvalidUrlException exception = assertThrows(InvalidUrlException.class,
            () -> urlService.updateUrl("abc123", request));
        assertEquals("Expiry must be between 1 and 365 days", exception.getMessage());
        verify(urlRepository, never()).updateExpiry(anyString(), any());
    }

    // ========== Delete URL Tests ==========

    @Test
    void should_softDeleteUrl_when_validShortCodeProvided() {
        // Given
        UrlMapping existing = new UrlMapping(
            "abc123",
            "https://example.com/test",
            1704067200L,
            "anonymous",
            true,
            null,
            false,
            10L
        );
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(existing));
        doNothing().when(urlRepository).softDelete("abc123");

        // When
        urlService.deleteUrl("abc123");

        // Then
        verify(urlRepository, times(1)).findByShortCode("abc123");
        verify(urlRepository, times(1)).softDelete("abc123");
    }

    @Test
    void should_throwUrlNotFoundException_when_deleteCalledOnNonExistentUrl() {
        // Given
        when(urlRepository.findByShortCode("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        UrlNotFoundException exception = assertThrows(UrlNotFoundException.class,
            () -> urlService.deleteUrl("nonexistent"));
        assertEquals("Short URL not found: nonexistent", exception.getMessage());
        verify(urlRepository, never()).softDelete(anyString());
    }
}
