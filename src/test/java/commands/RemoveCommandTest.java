package commands;

import cli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import service.DependencyResolver;
import service.InitializrClient;
import support.SampleMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoveCommandTest {

    private static final String TARGET_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.postgresql</groupId>
                        <artifactId>postgresql</artifactId>
                        <scope>runtime</scope>
                    </dependency>
                </dependencies>
            </project>
            """;

    /** What Initializr returns for the requested id — maps the id to its Maven coordinate. */
    private static final String REFERENCE_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <dependencies>
                    <dependency>
                        <groupId>org.postgresql</groupId>
                        <artifactId>postgresql</artifactId>
                        <scope>runtime</scope>
                    </dependency>
                </dependencies>
            </project>
            """;

    private RemoveCommand command(String referencePom) {
        InitializrClient client = mock(InitializrClient.class);
        when(client.fetchPom(any())).thenReturn(referencePom);
        return new RemoveCommand(new DependencyResolver(SampleMetadata.service(), client));
    }

    private int run(RemoveCommand cmd, String... args) {
        return Main.configure(new CommandLine(cmd)).execute(args);
    }

    @Test
    void deletesMatchingDependencyFromPom(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), TARGET_POM);

        int code = run(command(REFERENCE_POM), "postgresql", "--file", pom.toString());

        assertEquals(0, code);
        String result = Files.readString(pom);
        assertFalse(result.contains("<artifactId>postgresql</artifactId>"));
        assertTrue(result.contains("spring-boot-starter-web"), "unrelated deps kept");
    }

    @Test
    void dryRunLeavesFileUntouched(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), TARGET_POM);

        int code = run(command(REFERENCE_POM), "postgresql", "--file", pom.toString(), "--dry-run");

        assertEquals(0, code);
        assertEquals(TARGET_POM, Files.readString(pom));
    }

    @Test
    void nothingToRemoveWhenAbsentIsANoOp(@TempDir Path dir) throws IOException {
        // Reference resolves to a coordinate that isn't in the target pom.
        String reference = """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;
        Path pom = Files.writeString(dir.resolve("pom.xml"), TARGET_POM);

        int code = run(command(reference), "data-jpa", "--file", pom.toString());

        assertEquals(0, code);
        assertEquals(TARGET_POM, Files.readString(pom));
    }

    @Test
    void missingPomIsAUsageError(@TempDir Path dir) {
        int code = run(command(REFERENCE_POM), "postgresql", "--file", dir.resolve("nope.xml").toString());
        assertEquals(2, code);
    }

    @Test
    void unknownDependencyIsRejected(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), TARGET_POM);

        int code = run(command(REFERENCE_POM), "totally-not-a-dep", "--file", pom.toString());

        assertNotEquals(0, code);
        assertEquals(TARGET_POM, Files.readString(pom));
    }
}
