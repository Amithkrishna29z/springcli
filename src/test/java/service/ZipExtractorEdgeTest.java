package service;

import exception.ExtractionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipExtractorEdgeTest {

    private final ZipExtractor extractor = new ZipExtractor();

    /** Builds a zip; a null content marks a directory entry (name should end with '/'). */
    private byte[] zip(String name, String content) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            z.putNextEntry(new ZipEntry(name));
            if (content != null) {
                z.write(content.getBytes(StandardCharsets.UTF_8));
            }
            z.closeEntry();
        }
        return b.toByteArray();
    }

    private byte[] emptyZip() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        new ZipOutputStream(b).close();
        return b.toByteArray();
    }

    @Test
    void emptyArchiveCreatesAnEmptyTargetDirectory(@TempDir Path parent) throws IOException {
        Path target = parent.resolve("out");
        extractor.extract(emptyZip(), target);
        assertTrue(Files.isDirectory(target));
        try (Stream<Path> s = Files.list(target)) {
            assertTrue(s.findAny().isEmpty());
        }
    }

    @Test
    void directoryEntryIsCreated(@TempDir Path target) throws IOException {
        extractor.extract(zip("src/main/", null), target);
        assertTrue(Files.isDirectory(target.resolve("src/main")));
    }

    @Test
    void extractingOntoAnExistingFileFails(@TempDir Path target) throws IOException {
        Files.writeString(target.resolve("pom.xml"), "existing");
        byte[] archive = zip("pom.xml", "new");
        assertThrows(ExtractionException.class, () -> extractor.extract(archive, target));
    }

    @Test
    void fileOverloadExtractsFromDisk(@TempDir Path parent) throws IOException {
        Path zipFile = parent.resolve("a.zip");
        Files.write(zipFile, zip("readme.txt", "hi"));
        Path target = parent.resolve("out");
        extractor.extract(zipFile, target);
        assertEquals("hi", Files.readString(target.resolve("readme.txt")));
    }

    @Test
    void nestedTraversalEntryIsRejected(@TempDir Path target) throws IOException {
        byte[] archive = zip("a/../../evil.txt", "x");
        assertThrows(ExtractionException.class, () -> extractor.extract(archive, target));
    }
}
