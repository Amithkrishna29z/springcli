package util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilsTest {

    @Test
    void emptyDirectoryIsNotNonEmpty(@TempDir Path dir) {
        assertFalse(FileUtils.isNonEmptyDirectory(dir));
    }

    @Test
    void directoryWithAFileIsNonEmpty(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.txt"), "x");
        assertTrue(FileUtils.isNonEmptyDirectory(dir));
    }

    @Test
    void nonexistentPathIsNotNonEmpty(@TempDir Path dir) {
        assertFalse(FileUtils.isNonEmptyDirectory(dir.resolve("missing")));
    }

    @Test
    void aRegularFileIsNotANonEmptyDirectory(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("f");
        Files.writeString(f, "x");
        assertFalse(FileUtils.isNonEmptyDirectory(f));
    }

    @Test
    void deleteQuietlyRemovesNestedTree(@TempDir Path dir) throws IOException {
        Path sub = dir.resolve("a/b/c");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("f.txt"), "x");
        FileUtils.deleteQuietly(dir.resolve("a"));
        assertFalse(Files.exists(dir.resolve("a")));
    }

    @Test
    void deleteQuietlyIsSafeForNullAndMissing(@TempDir Path dir) {
        assertDoesNotThrow(() -> FileUtils.deleteQuietly(null));
        assertDoesNotThrow(() -> FileUtils.deleteQuietly(dir.resolve("nope")));
    }
}
