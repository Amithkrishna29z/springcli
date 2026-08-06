package service;

import exception.ExtractionException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts a Spring Initializr starter archive into a target directory.
 *
 * <p>Guards against the "zip slip" path-traversal vulnerability by verifying that every resolved
 * entry stays within the destination directory before writing it.</p>
 */
public class ZipExtractor {

    /**
     * Extracts a ZIP file from disk into {@code targetDir}.
     *
     * @param zipFile   the archive on disk
     * @param targetDir the destination directory (created if absent)
     * @throws ExtractionException if the archive is malformed or cannot be written
     */
    public void extract(Path zipFile, Path targetDir) {
        try (InputStream in = Files.newInputStream(zipFile)) {
            extract(in, targetDir);
        } catch (IOException e) {
            throw new ExtractionException("Failed to read the downloaded project archive.", e);
        }
    }

    /**
     * Extracts ZIP bytes held in memory into {@code targetDir}. Convenient for tests.
     *
     * @param zipBytes  the raw archive content
     * @param targetDir the destination directory (created if absent)
     * @throws ExtractionException if the archive is malformed or cannot be written
     */
    public void extract(byte[] zipBytes, Path targetDir) {
        extract(new ByteArrayInputStream(zipBytes), targetDir);
    }

    /** Core extraction routine operating on any input stream. */
    public void extract(InputStream zipStream, Path targetDir) {
        Path destination = targetDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(destination);
        } catch (IOException e) {
            throw new ExtractionException("Could not create target directory: " + destination, e);
        }

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(zipStream))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = destination.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(destination)) {
                    throw new ExtractionException(
                            "Refusing to extract entry outside target directory: " + entry.getName(), null);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zis, resolved);
                }
                zis.closeEntry();
            }
        } catch (ExtractionException e) {
            throw e;
        } catch (IOException e) {
            throw new ExtractionException("Failed to extract the downloaded project archive.", e);
        }
    }
}
