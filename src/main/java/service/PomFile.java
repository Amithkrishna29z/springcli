package service;

import exception.SpringCliException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** A project's {@code pom.xml}: where it lives and the content it had when it was read. */
public record PomFile(Path path, String xml) {

    public static PomFile read(Path path) {
        try {
            return new PomFile(path, Files.readString(path));
        } catch (IOException e) {
            throw new SpringCliException("Could not read " + path, e);
        }
    }

    public Path fileName() {
        return path.getFileName();
    }

    /** Replaces the file's content with {@code newXml}. */
    public void write(String newXml) {
        try {
            Files.writeString(path, newXml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SpringCliException("Could not write " + path, e);
        }
    }
}
