package inc.skt.quicklink.repository;

import inc.skt.quicklink.model.UrlMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryUrlRepositoryTest {

    private InMemoryUrlRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUrlRepository();
    }

    // ========== Save Tests ==========

    @Test
    void should_saveUrlMapping_when_validMappingProvided() {
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

        // When
        UrlMapping saved = repository.save(mapping);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getShortCode()).isEqualTo("abc123");
        assertThat(repository.existsByShortCode("abc123")).isTrue();
    }

    // ========== Find Tests ==========

    @Test
    void should_returnUrlMapping_when_shortCodeExists() {
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
        repository.save(mapping);

        // When
        Optional<UrlMapping> found = repository.findByShortCode("abc123");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getShortCode()).isEqualTo("abc123");
        assertThat(found.get().getLongUrl()).isEqualTo("https://example.com/test");
    }

    @Test
    void should_returnEmpty_when_shortCodeDoesNotExist() {
        // When
        Optional<UrlMapping> found = repository.findByShortCode("nonexistent");

        // Then
        assertThat(found).isEmpty();
    }

    // ========== Exists Tests ==========

    @Test
    void should_returnTrue_when_shortCodeExists() {
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
        repository.save(mapping);

        // When
        boolean exists = repository.existsByShortCode("abc123");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void should_returnFalse_when_shortCodeDoesNotExist() {
        // When
        boolean exists = repository.existsByShortCode("nonexistent");

        // Then
        assertThat(exists).isFalse();
    }

    // ========== Soft Delete Tests ==========

    @Test
    void should_setIsActiveToFalse_when_softDeleteCalled() {
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
        repository.save(mapping);

        // When
        repository.softDelete("abc123");

        // Then
        Optional<UrlMapping> found = repository.findByShortCode("abc123");
        assertThat(found).isPresent();
        assertThat(found.get().getIsActive()).isFalse();
    }

    @Test
    void should_doNothing_when_softDeleteCalledOnNonExistentShortCode() {
        // When & Then (no exception should be thrown)
        repository.softDelete("nonexistent");
    }

    // ========== Update Expiry Tests ==========

    @Test
    void should_updateExpiryTime_when_validExpiryProvided() {
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
        repository.save(mapping);

        // When
        Long newExpiry = 1706659200L;
        repository.updateExpiry("abc123", newExpiry);

        // Then
        Optional<UrlMapping> found = repository.findByShortCode("abc123");
        assertThat(found).isPresent();
        assertThat(found.get().getExpiresAt()).isEqualTo(newExpiry);
    }

    @Test
    void should_removeExpiry_when_nullExpiryProvided() {
        // Given
        UrlMapping mapping = new UrlMapping(
            "abc123",
            "https://example.com/test",
            1704067200L,
            "anonymous",
            true,
            1706659200L,
            false,
            0L
        );
        repository.save(mapping);

        // When
        repository.updateExpiry("abc123", null);

        // Then
        Optional<UrlMapping> found = repository.findByShortCode("abc123");
        assertThat(found).isPresent();
        assertThat(found.get().getExpiresAt()).isNull();
    }

    @Test
    void should_doNothing_when_updateExpiryCalledOnNonExistentShortCode() {
        // When & Then (no exception should be thrown)
        repository.updateExpiry("nonexistent", 1706659200L);
    }

    // ========== Increment Click Count Tests ==========

    @Test
    void should_incrementClickCount_when_urlExists() {
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
        repository.save(mapping);

        // When
        repository.incrementClickCount("abc123");

        // Then
        Optional<UrlMapping> found = repository.findByShortCode("abc123");
        assertThat(found).isPresent();
        assertThat(found.get().getClickCount()).isEqualTo(1L);
    }

    @Test
    void should_incrementClickCountMultipleTimes_when_calledRepeatedly() {
        // Given
        UrlMapping mapping = new UrlMapping(
            "abc123",
            "https://example.com/test",
            1704067200L,
            "anonymous",
            true,
            null,
            false,
            5L
        );
        repository.save(mapping);

        // When
        repository.incrementClickCount("abc123");
        repository.incrementClickCount("abc123");
        repository.incrementClickCount("abc123");

        // Then
        Optional<UrlMapping> found = repository.findByShortCode("abc123");
        assertThat(found).isPresent();
        assertThat(found.get().getClickCount()).isEqualTo(8L);
    }

    @Test
    void should_doNothing_when_incrementClickCountCalledOnNonExistentShortCode() {
        // When & Then (no exception should be thrown)
        repository.incrementClickCount("nonexistent");
    }

    // ========== Integration Tests ==========

    @Test
    void should_handleMultipleOperations_when_performedSequentially() {
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

        // When
        repository.save(mapping);
        repository.incrementClickCount("abc123");
        repository.incrementClickCount("abc123");
        repository.updateExpiry("abc123", 1706659200L);

        // Then
        Optional<UrlMapping> found = repository.findByShortCode("abc123");
        assertThat(found).isPresent();
        assertThat(found.get().getClickCount()).isEqualTo(2L);
        assertThat(found.get().getExpiresAt()).isEqualTo(1706659200L);
        assertThat(found.get().getIsActive()).isTrue();
    }

    @Test
    void should_maintainSeparateCounters_when_multipleUrlsExist() {
        // Given
        UrlMapping mapping1 = new UrlMapping("url1", "https://example.com/1", 1704067200L, "anonymous", true, null, false, 0L);
        UrlMapping mapping2 = new UrlMapping("url2", "https://example.com/2", 1704067200L, "anonymous", true, null, false, 0L);
        repository.save(mapping1);
        repository.save(mapping2);

        // When
        repository.incrementClickCount("url1");
        repository.incrementClickCount("url1");
        repository.incrementClickCount("url2");

        // Then
        assertThat(repository.findByShortCode("url1").get().getClickCount()).isEqualTo(2L);
        assertThat(repository.findByShortCode("url2").get().getClickCount()).isEqualTo(1L);
    }
}
