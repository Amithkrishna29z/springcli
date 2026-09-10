package commands;

import cli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import service.VulnerabilityService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditCommandTest {

    /** A pom with one explicitly-versioned dependency (the only kind 'audit' can check). */
    private static final String PINNED_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework</groupId>
                        <artifactId>spring-web</artifactId>
                        <version>5.3.0</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    @SuppressWarnings("unchecked")
    private AuditCommand command(String osvBody) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn(osvBody);
        doReturn(resp).when(http).send(any(HttpRequest.class), any());
        return new AuditCommand(new VulnerabilityService(http, "https://api.osv.dev"));
    }

    private String run(int[] code, AuditCommand cmd, String... args) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            code[0] = Main.configure(new CommandLine(cmd)).execute(args);
        } finally {
            System.setOut(original);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void reportsVulnerabilitiesAndExitsNonZero(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);
        String body = """
                {"vulns":[{"id":"GHSA-2rmj-mq67-h97g","summary":"Spring DoS",
                  "aliases":["CVE-2024-38809"],"database_specific":{"severity":"MODERATE"}}]}
                """;

        int[] code = new int[1];
        String out = run(code, command(body), "--file", pom.toString());

        assertEquals(1, code[0]); // non-zero exit signals findings (usable as a CI gate)
        assertTrue(out.contains("CVE-2024-38809"));
        assertTrue(out.contains("org.springframework:spring-web:5.3.0"));
    }

    @Test
    void cleanProjectExitsZero(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);

        int[] code = new int[1];
        String out = run(code, command("{}"), "--file", pom.toString());

        assertEquals(0, code[0]);
        assertTrue(out.toLowerCase().contains("no known vulnerabilities"));
    }

    @Test
    void noPinnedVersionsIsAWarningNotAFailure(@TempDir Path dir) throws Exception {
        String managedPom = """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;
        Path pom = Files.writeString(dir.resolve("pom.xml"), managedPom);

        int[] code = new int[1];
        String out = run(code, command("{}"), "--file", pom.toString());

        assertEquals(0, code[0]);
        assertTrue(out.toLowerCase().contains("no pinned"));
    }

    @Test
    void missingPomIsAUsageError(@TempDir Path dir) throws Exception {
        int[] code = new int[1];
        run(code, command("{}"), "--file", dir.resolve("nope.xml").toString());
        assertEquals(2, code[0]);
    }
}
