package commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepsCommandTest {

    private static final String POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.3.2</version>
                </parent>
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

    private String runCapturingOut(int[] code, String... args) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            code[0] = new CommandLine(new DepsCommand()).execute(args);
        } finally {
            System.setOut(original);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void listsInstalledDependencies(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), POM);

        int[] code = new int[1];
        String out = runCapturingOut(code, "--file", pom.toString());

        assertEquals(0, code[0]);
        assertTrue(out.contains("org.springframework.boot:spring-boot-starter-web"));
        assertTrue(out.contains("org.springframework.boot:spring-boot-starter-test"));
        assertTrue(out.contains("Spring Boot 3.3.2"), "shows the parent Boot version");
        assertTrue(out.contains("test"), "shows the scope annotation");
    }

    @Test
    void missingPomIsAUsageError(@TempDir Path dir) {
        int[] code = new int[1];
        runCapturingOut(code, "--file", dir.resolve("nope.xml").toString());
        assertEquals(2, code[0]);
    }

    @Test
    void warnsWhenNoDependencies(@TempDir Path dir) throws IOException {
        String pom = "<project><artifactId>demo</artifactId></project>";
        Path file = Files.writeString(dir.resolve("pom.xml"), pom);

        int[] code = new int[1];
        String out = runCapturingOut(code, "--file", file.toString());

        assertEquals(0, code[0]);
        assertTrue(out.toLowerCase().contains("no dependencies"));
    }
}
