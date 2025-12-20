package inc.skt.quicklink.service;

import inc.skt.quicklink.repository.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private TokenRepository tokenRepository;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(tokenRepository);
    }

    // ========== Range Allocation Tests ==========

    @Test
    void should_allocateNewRange_when_firstIdRequested() {
        // Given
        when(tokenRepository.incrementAndGet("global_counter", 100L)).thenReturn(100L);

        // When
        long id = tokenService.getNextId();

        // Then
        assertEquals(0L, id);
        verify(tokenRepository, times(1)).incrementAndGet("global_counter", 100L);
    }

    @Test
    void should_returnSequentialIds_when_withinSameRange() {
        // Given
        when(tokenRepository.incrementAndGet("global_counter", 100L)).thenReturn(100L);

        // When
        long id1 = tokenService.getNextId();
        long id2 = tokenService.getNextId();
        long id3 = tokenService.getNextId();

        // Then
        assertEquals(0L, id1);
        assertEquals(1L, id2);
        assertEquals(2L, id3);
        verify(tokenRepository, times(1)).incrementAndGet("global_counter", 100L);
    }

    @Test
    void should_allocateNewRange_when_currentRangeExhausted() {
        // Given
        when(tokenRepository.incrementAndGet("global_counter", 100L))
            .thenReturn(100L)  // First range: 0-99
            .thenReturn(200L); // Second range: 100-199

        // When - Exhaust first range
        for (int i = 0; i < 100; i++) {
            tokenService.getNextId();
        }
        
        // Request ID from second range
        long id = tokenService.getNextId();

        // Then
        assertEquals(100L, id);
        verify(tokenRepository, times(2)).incrementAndGet("global_counter", 100L);
    }

    @Test
    void should_allocateMultipleRanges_when_manyIdsRequested() {
        // Given
        when(tokenRepository.incrementAndGet("global_counter", 100L))
            .thenReturn(100L)   // Range 1: 0-99
            .thenReturn(200L)   // Range 2: 100-199
            .thenReturn(300L);  // Range 3: 200-299

        // When - Request 250 IDs
        long lastId = 0;
        for (int i = 0; i < 250; i++) {
            lastId = tokenService.getNextId();
        }

        // Then
        assertEquals(249L, lastId);
        verify(tokenRepository, times(3)).incrementAndGet("global_counter", 100L);
    }

    // ========== Thread Safety Tests ==========

    @Test
    void should_returnUniqueIds_when_calledConcurrently() throws InterruptedException {
        // Given
        when(tokenRepository.incrementAndGet("global_counter", 100L))
            .thenReturn(100L)
            .thenReturn(200L);

        // When - Simulate concurrent access
        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                tokenService.getNextId();
            }
        });

        Thread thread2 = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                tokenService.getNextId();
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Then - Should have allocated 100 IDs total
        long nextId = tokenService.getNextId();
        assertEquals(100L, nextId); // First ID from second range
    }

    // ========== Edge Cases ==========

    @Test
    void should_handleLargeRangeEnd_when_manyRangesAllocated() {
        // Given
        when(tokenRepository.incrementAndGet("global_counter", 100L))
            .thenReturn(1000000L);

        // When
        long id = tokenService.getNextId();

        // Then
        assertEquals(999900L, id);
        verify(tokenRepository, times(1)).incrementAndGet("global_counter", 100L);
    }

    @Test
    void should_allocateExactly100Ids_when_oneRangeAllocated() {
        // Given
        when(tokenRepository.incrementAndGet("global_counter", 100L))
            .thenReturn(100L)
            .thenReturn(200L);

        // When - Get exactly 100 IDs
        for (int i = 0; i < 100; i++) {
            tokenService.getNextId();
        }

        // Then - Next call should trigger new range allocation
        long id = tokenService.getNextId();
        assertEquals(100L, id);
        verify(tokenRepository, times(2)).incrementAndGet("global_counter", 100L);
    }

    @Test
    void should_startFromZero_when_firstRangeAllocated() {
        // Given
        when(tokenRepository.incrementAndGet("global_counter", 100L)).thenReturn(100L);

        // When
        long firstId = tokenService.getNextId();

        // Then
        assertEquals(0L, firstId);
    }

    @Test
    void should_incrementCorrectly_when_multipleCallsInSequence() {
        // Given
        when(tokenRepository.incrementAndGet("global_counter", 100L)).thenReturn(100L);

        // When
        long id1 = tokenService.getNextId();
        long id2 = tokenService.getNextId();
        long id3 = tokenService.getNextId();
        long id4 = tokenService.getNextId();
        long id5 = tokenService.getNextId();

        // Then
        assertEquals(0L, id1);
        assertEquals(1L, id2);
        assertEquals(2L, id3);
        assertEquals(3L, id4);
        assertEquals(4L, id5);
    }

    // ========== Range Boundary Tests ==========

    @Test
    void should_allocateNewRange_when_exactlyAtBoundary() {
        // Given
        when(tokenRepository.incrementAndGet("global_counter", 100L))
            .thenReturn(100L)
            .thenReturn(200L);

        // When - Get 99 IDs (0-98)
        for (int i = 0; i < 99; i++) {
            tokenService.getNextId();
        }
        
        long lastInRange = tokenService.getNextId(); // ID 99
        long firstInNewRange = tokenService.getNextId(); // Should trigger new range

        // Then
        assertEquals(99L, lastInRange);
        assertEquals(100L, firstInNewRange);
        verify(tokenRepository, times(2)).incrementAndGet("global_counter", 100L);
    }
}
