package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Small filesystem helpers shared across services. */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * @return {@code true} if the path exists and is a directory that contains at least one entry.
     */
    public static boolean isNonEmptyDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(path)) {
            return entries.findAny().isPresent();
        } catch (IOException e) {
            // If we cannot inspect it, treat it as occupied to stay on the safe side.
            return true;
        }
    }

    /**
     * Recursively deletes {@code path} if it exists. Best-effort: intended for cleaning up partial
     * output on failure. Never throws.
     */
    public static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best effort
                    }
                });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
