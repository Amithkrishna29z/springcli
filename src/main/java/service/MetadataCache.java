package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * On-disk cache of the raw Spring Initializr metadata JSON, so {@code search}/{@code list}/{@code new}
 * are fast and keep working offline. Entries are stored with a timestamp and considered "fresh" for
 * {@code ttl}; {@link #readAny()} additionally exposes a stale entry as an offline fallback.
 *
 * <p>Use {@link #disabled()} to turn caching off (e.g. in tests or via an env flag); its reads return
 * empty and its writes are no-ops.</p>
 */
public class MetadataCache {

    private final Path file;
    private final Duration ttl;
    private final ObjectMapper mapper = new ObjectMapper();

    private MetadataCache(Path file, Duration ttl) {
        this.file = file;
        this.ttl = ttl;
    }

    public static MetadataCache disabled() {
        return new MetadataCache(null, Duration.ZERO);
    }

    public static MetadataCache onDisk(Path file, Duration ttl) {
        return new MetadataCache(file, ttl);
    }

    public boolean enabled() {
        return file != null;
    }

    /** @return the cached metadata JSON if present and newer than {@code ttl}, else empty. */
    public Optional<String> readFresh() {
        return read(true);
    }

    /** @return the cached metadata JSON regardless of age (offline fallback), else empty. */
    public Optional<String> readAny() {
        return read(false);
    }

    private Optional<String> read(boolean requireFresh) {
        if (file == null) {
            return Optional.empty();
        }
        try {
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            JsonNode root = mapper.readTree(Files.readString(file));
            JsonNode metadata = root.get("metadata");
            if (metadata == null || metadata.isMissingNode()) {
                return Optional.empty();
            }
            if (requireFresh) {
                long fetchedAt = root.path("fetchedAt").asLong(0);
                if (System.currentTimeMillis() - fetchedAt >= ttl.toMillis()) {
                    return Optional.empty();
                }
            }
            return Optional.of(mapper.writeValueAsString(metadata));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Best-effort write of {@code metadataJson} with the current timestamp. Never throws. */
    public void write(String metadataJson) {
        if (file == null) {
            return;
        }
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("fetchedAt", System.currentTimeMillis());
            root.set("metadata", mapper.readTree(metadataJson));
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(root));
        } catch (Exception ignored) {
            // A missing/failed cache write just means we re-fetch next time.
        }
    }
}
