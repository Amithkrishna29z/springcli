package service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataCacheTest {

    private static final String JSON = "{\"bootVersion\":{\"default\":\"3.3.2\"},\"x\":[1,2,3]}";

    @Test
    void writeThenReadFreshReturnsEquivalentJson(@TempDir Path dir) {
        MetadataCache cache = MetadataCache.onDisk(dir.resolve("m.json"), Duration.ofHours(1));
        cache.write(JSON);
        Optional<String> read = cache.readFresh();
        assertTrue(read.isPresent());
        assertTrue(read.get().contains("3.3.2"));
        assertTrue(read.get().contains("[1,2,3]"));
    }

    @Test
    void readFreshExpiresAfterTtl(@TempDir Path dir) {
        MetadataCache cache = MetadataCache.onDisk(dir.resolve("m.json"), Duration.ZERO);
        cache.write(JSON);
        // TTL of zero means nothing is ever "fresh".
        assertTrue(cache.readFresh().isEmpty());
        // …but it is still available as a stale fallback.
        assertTrue(cache.readAny().isPresent());
    }

    @Test
    void readReturnsEmptyWhenFileMissing(@TempDir Path dir) {
        MetadataCache cache = MetadataCache.onDisk(dir.resolve("missing.json"), Duration.ofHours(1));
        assertTrue(cache.readFresh().isEmpty());
        assertTrue(cache.readAny().isEmpty());
    }

    @Test
    void corruptFileReadsAsEmpty(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("m.json");
        Files.writeString(file, "{ not valid");
        MetadataCache cache = MetadataCache.onDisk(file, Duration.ofHours(1));
        assertTrue(cache.readFresh().isEmpty());
    }

    @Test
    void writingMalformedJsonIsANoOp(@TempDir Path dir) {
        Path file = dir.resolve("m.json");
        MetadataCache cache = MetadataCache.onDisk(file, Duration.ofHours(1));
        cache.write("{ not json");
        assertFalse(Files.exists(file));
    }

    @Test
    void disabledCacheReadsEmptyAndDoesNotWrite(@TempDir Path dir) {
        MetadataCache cache = MetadataCache.disabled();
        cache.write(JSON);
        assertFalse(cache.enabled());
        assertTrue(cache.readFresh().isEmpty());
        assertTrue(cache.readAny().isEmpty());
    }
}
