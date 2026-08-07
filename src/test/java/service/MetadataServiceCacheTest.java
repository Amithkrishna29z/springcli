package service;

import exception.NetworkException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.SampleMetadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataServiceCacheTest {

    @Test
    void freshCacheIsUsedWithoutHittingNetwork(@TempDir Path dir) {
        MetadataCache cache = MetadataCache.onDisk(dir.resolve("m.json"), Duration.ofHours(1));
        cache.write(SampleMetadata.JSON);

        InitializrClient client = mock(InitializrClient.class);
        MetadataService svc = new MetadataService(client, cache);

        assertEquals("3.3.2", svc.getMetadata().bootVersion().defaultValue());
        verify(client, never()).fetchMetadata();
    }

    @Test
    void networkFetchPopulatesTheCache(@TempDir Path dir) {
        Path file = dir.resolve("m.json");
        MetadataCache cache = MetadataCache.onDisk(file, Duration.ofHours(1));

        InitializrClient client = mock(InitializrClient.class);
        when(client.fetchMetadata()).thenReturn(SampleMetadata.JSON);

        new MetadataService(client, cache).getMetadata();

        assertTrue(Files.exists(file));
        assertTrue(cache.readFresh().isPresent());
    }

    @Test
    void staleCacheIsUsedWhenNetworkFails(@TempDir Path dir) {
        // TTL zero => never fresh, so the service always tries the network first.
        MetadataCache cache = MetadataCache.onDisk(dir.resolve("m.json"), Duration.ZERO);
        cache.write(SampleMetadata.JSON);

        InitializrClient client = mock(InitializrClient.class);
        when(client.fetchMetadata()).thenThrow(new NetworkException("offline"));

        MetadataService svc = new MetadataService(client, cache);
        assertEquals("3.3.2", svc.getMetadata().bootVersion().defaultValue());
    }

    @Test
    void networkFailureWithNoCacheStillThrows(@TempDir Path dir) {
        MetadataCache cache = MetadataCache.onDisk(dir.resolve("missing.json"), Duration.ofHours(1));
        InitializrClient client = mock(InitializrClient.class);
        when(client.fetchMetadata()).thenThrow(new NetworkException("offline"));

        assertThrows(NetworkException.class, () -> new MetadataService(client, cache).getMetadata());
    }
}
