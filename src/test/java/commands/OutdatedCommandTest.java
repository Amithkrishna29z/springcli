package commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import support.SampleMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutdatedCommandTest {

    /** SampleMetadata's latest (default) Boot version is 3.3.2. */
    private static String pomWithBoot(String version) {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>%s</version>
                    </parent>
                    <artifactId>demo</artifactId>
                </project>
                """.formatted(version);
    }

    private String run(int[] code, Path pom) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            OutdatedCommand cmd = new OutdatedCommand(SampleMetadata.service());
            code[0] = new CommandLine(cmd).execute("--file", pom.toString());
        } finally {
            System.setOut(original);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void reportsWhenBootVersionIsBehind(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), pomWithBoot("3.2.8"));

        int[] code = new int[1];
        String out = run(code, pom);

        assertEquals(0, code[0]);
        assertTrue(out.contains("3.2.8"));
        assertTrue(out.contains("3.3.2"));
        assertTrue(out.toLowerCase().contains("available"));
    }

    @Test
    void reportsUpToDateWhenOnLatest(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), pomWithBoot("3.3.2"));

        int[] code = new int[1];
        String out = run(code, pom);

        assertEquals(0, code[0]);
        assertTrue(out.toLowerCase().contains("up to date"));
    }

    @Test
    void errorsWhenNoSpringBootParent(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"),
                "<project><artifactId>demo</artifactId></project>");

        int[] code = new int[1];
        run(code, pom);

        assertEquals(2, code[0]);
    }
}
