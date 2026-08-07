package service;

import exception.ExtractionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipExtractorTest {

    private final ZipExtractor extractor = new ZipExtractor();

    private byte[] zip(String... nameThenContentPairs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < nameThenContentPairs.length; i += 2) {
                zos.putNextEntry(new ZipEntry(nameThenContentPairs[i]));
                zos.write(nameThenContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    @Test
    void extractsFilesAndNestedDirectories(@TempDir Path target) throws IOException {
        byte[] archive = zip(
                "pom.xml", "<project/>",
                "src/main/java/App.java", "class App {}");

        extractor.extract(archive, target);

        assertEquals("<project/>", Files.readString(target.resolve("pom.xml")));
        assertTrue(Files.exists(target.resolve("src/main/java/App.java")));
    }

    @Test
    void createsTargetDirectoryWhenMissing(@TempDir Path parent) throws IOException {
        Path target = parent.resolve("brand-new");
        assertFalse(Files.exists(target));
        extractor.extract(zip("a.txt", "hi"), target);
        assertTrue(Files.exists(target.resolve("a.txt")));
    }

    @Test
    void rejectsZipSlipEntries(@TempDir Path target) throws IOException {
        byte[] malicious = zip("../evil.txt", "pwned");
        assertThrows(ExtractionException.class, () -> extractor.extract(malicious, target));
    }

    @Test
    void truncatedArchiveRaisesExtractionException(@TempDir Path target) throws IOException {

        byte[] full = zip("data.bin", "x".repeat(2000));
        byte[] truncated = java.util.Arrays.copyOf(full, 40);
        assertThrows(ExtractionException.class, () -> extractor.extract(truncated, target));
    }
}
