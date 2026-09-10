package service;

import model.Architecture;
import model.ProjectRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureScaffolderTest {

    @TempDir
    Path tempDir;

    private final ArchitectureScaffolder scaffolder = new ArchitectureScaffolder();

    /** Creates the flat layout Initializr produces, and returns the project root. */
    private Path project(String language, String packageName) throws IOException {
        Path base = tempDir.resolve("src/main/" + language + "/" + packageName.replace('.', '/'));
        Files.createDirectories(base);
        Files.writeString(base.resolve("DemoApplication.java"), "");
        return tempDir;
    }

    private ProjectRequest request(String language, String packageName, Architecture architecture) {
        return ProjectRequest.builder()
                .bootVersion("3.3.2")
                .language(language)
                .packageName(packageName)
                .architecture(architecture)
                .build();
    }

    @Test
    void layeredCreatesEachPackageWithAGitkeep() throws IOException {
        Path root = project("java", "com.acme.demo");
        scaffolder.scaffold(request("java", "com.acme.demo", Architecture.LAYERED), root);

        Path base = root.resolve("src/main/java/com/acme/demo");
        for (String pkg : Architecture.LAYERED.packages()) {
            assertTrue(Files.isDirectory(base.resolve(pkg)), pkg + " should exist");
            assertTrue(Files.isRegularFile(base.resolve(pkg).resolve(".gitkeep")), pkg + " should have .gitkeep");
        }
        assertTrue(Files.isRegularFile(base.resolve("DemoApplication.java")), "generated sources are untouched");
    }

    @Test
    void nestedPackagesAreCreatedForClean() throws IOException {
        Path root = project("java", "com.acme.demo");
        scaffolder.scaffold(request("java", "com.acme.demo", Architecture.CLEAN), root);

        Path base = root.resolve("src/main/java/com/acme/demo");
        assertTrue(Files.isDirectory(base.resolve("domain/model")));
        assertTrue(Files.isDirectory(base.resolve("infrastructure/persistence")));
        assertTrue(Files.isDirectory(base.resolve("presentation/controller")));
    }

    @Test
    void kotlinProjectsScaffoldUnderTheKotlinSourceRoot() throws IOException {
        Path root = project("kotlin", "com.acme.demo");
        scaffolder.scaffold(request("kotlin", "com.acme.demo", Architecture.HEXAGONAL), root);

        assertTrue(Files.isDirectory(root.resolve("src/main/kotlin/com/acme/demo/adapter/inbound/web")));
        assertFalse(Files.exists(root.resolve("src/main/java")));
    }

    @Test
    void noneLeavesTheProjectUntouched() throws IOException {
        Path root = project("java", "com.acme.demo");
        scaffolder.scaffold(request("java", "com.acme.demo", Architecture.NONE), root);

        try (var entries = Files.list(root.resolve("src/main/java/com/acme/demo"))) {
            assertEquals(1L, entries.count());
        }
    }

    @Test
    void missingBasePackageIsANoOp() {
        scaffolder.scaffold(request("java", "com.acme.demo", Architecture.LAYERED), tempDir);

        assertFalse(Files.exists(tempDir.resolve("src")));
    }
}
