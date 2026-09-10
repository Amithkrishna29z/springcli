package commands;

import cli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import service.DependencyResolver;
import service.InitializrClient;
import service.MetadataService;
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

class AddCommandTest {

    private static final String TARGET_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-test</artifactId>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
            </project>
            """;

    private static final String REFERENCE_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <dependencies>
                    <dependency>
                        <groupId>org.postgresql</groupId>
                        <artifactId>postgresql</artifactId>
                        <scope>runtime</scope>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-test</artifactId>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
            </project>
            """;

    private AddCommand command(String referencePom) {
        InitializrClient client = mock(InitializrClient.class);
        when(client.fetchPom(any())).thenReturn(referencePom);
        return new AddCommand(new DependencyResolver(SampleMetadata.service(), client));
    }

    private int run(AddCommand cmd, String... args) {
        return Main.configure(new CommandLine(cmd)).execute(args);
    }

    @Test
    void injectsNewDependencyIntoPom(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), TARGET_POM);

        int code = run(command(REFERENCE_POM), "postgresql", "--file", pom.toString());

        assertEquals(0, code);
        String result = Files.readString(pom);
        assertTrue(result.contains("<artifactId>postgresql</artifactId>"));
        assertTrue(result.contains("<scope>runtime</scope>"));
        assertTrue(result.contains("spring-boot-starter-web"), "existing deps kept");
    }

    @Test
    void dryRunLeavesFileUntouched(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), TARGET_POM);

        int code = run(command(REFERENCE_POM), "postgresql", "--file", pom.toString(), "--dry-run");

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
        assertFalse(Files.readString(pom).contains("totally-not-a-dep"));
    }
}
