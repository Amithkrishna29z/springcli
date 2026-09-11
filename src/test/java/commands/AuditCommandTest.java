package commands;

import audit.ArtifactResolver;
import audit.Auditor;
import cli.Main;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exception.UsageException;
import model.Artifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import service.PomFile;
import support.FakeOsv;
import support.SampleMetadata;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditCommandTest {

    /** A pom with one explicitly-versioned dependency (the only kind --no-resolve can check). */
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

    /** A Spring Boot 3.2.8 project; SampleMetadata offers 3.3.2 as the newer release. */
    private static final String BOOT_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.8</version>
                </parent>
                <artifactId>demo</artifactId>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

    private static final Artifact OLD_TOMCAT = new Artifact("org.apache.tomcat.embed", "tomcat-embed-core",
            "10.1.25", "compile", "org.springframework.boot:spring-boot-starter-web");
    private static final Artifact NEW_TOMCAT = new Artifact("org.apache.tomcat.embed", "tomcat-embed-core",
            "10.1.28", "compile", "org.springframework.boot:spring-boot-starter-web");

    /** Resolves to {@code current}, or to {@code upgraded} under any other Spring Boot version. */
    private static ArtifactResolver resolver(List<Artifact> current, List<Artifact> upgraded) {
        return new ArtifactResolver() {
            @Override
            public List<Artifact> resolve(PomFile pom) {
                return current;
            }

            @Override
            public List<Artifact> resolveWithSpringBoot(PomFile pom, String springBootVersion) {
                return upgraded;
            }
        };
    }

    /** A resolver that fails like Maven would; --no-resolve must never reach it. */
    private static ArtifactResolver failingResolver() {
        return new ArtifactResolver() {
            @Override
            public List<Artifact> resolve(PomFile pom) {
                throw new UsageException("Maven couldn't resolve the project's dependencies (exit code 1)");
            }

            @Override
            public List<Artifact> resolveWithSpringBoot(PomFile pom, String springBootVersion) {
                throw new UsageException("Maven couldn't resolve the project's dependencies (exit code 1)");
            }
        };
    }

    private static AuditCommand command(FakeOsv osv, ArtifactResolver resolver) throws Exception {
        return new AuditCommand(new Auditor(resolver, osv.service(), SampleMetadata.service()));
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

    private static FakeOsv tomcatVulnerableUntilUpgrade() {
        return new FakeOsv()
                .batch("GHSA-tc01")   // the project as it is
                .batch("")            // re-resolved with Spring Boot 3.3.2
                .vuln("GHSA-tc01", "HIGH", "CVE-2024-0001");
    }

    @Test
    void reportsPinnedVulnerabilitiesAndExitsNonZero(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);
        FakeOsv osv = new FakeOsv()
                .batch("GHSA-2rmj-mq67-h97g")
                .vuln("GHSA-2rmj-mq67-h97g", "MODERATE", "CVE-2024-38809");

        int[] code = new int[1];
        String out = run(code, command(osv, failingResolver()), "--no-resolve", "--file", pom.toString());

        assertEquals(1, code[0]); // non-zero exit signals findings (usable as a CI gate)
        assertTrue(out.contains("CVE-2024-38809"));
        assertTrue(out.contains("org.springframework:spring-web:5.3.0"));
    }

    @Test
    void cleanProjectExitsZero(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);

        int[] code = new int[1];
        String out = run(code, command(new FakeOsv().batch(""), failingResolver()),
                "--no-resolve", "--file", pom.toString());

        assertEquals(0, code[0]);
        assertTrue(out.toLowerCase().contains("no known vulnerabilities"));
    }

    @Test
    void noPinnedVersionsIsAWarningNotAFailure(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), BOOT_POM);

        int[] code = new int[1];
        String out = run(code, command(new FakeOsv(), failingResolver()), "--no-resolve", "--file", pom.toString());

        assertEquals(0, code[0]);
        assertTrue(out.toLowerCase().contains("no pinned"));
    }

    @Test
    void missingPomIsAUsageError(@TempDir Path dir) throws Exception {
        int[] code = new int[1];
        run(code, command(new FakeOsv(), failingResolver()), "--file", dir.resolve("nope.xml").toString());
        assertEquals(2, code[0]);
    }

    @Test
    void auditsResolvedDependenciesAndSuggestsTheFixingUpgrade(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), BOOT_POM);

        int[] code = new int[1];
        String out = run(code, command(tomcatVulnerableUntilUpgrade(), resolver(List.of(OLD_TOMCAT), List.of(NEW_TOMCAT))),
                "--file", pom.toString());

        assertEquals(1, code[0]);
        assertTrue(out.contains("org.apache.tomcat.embed:tomcat-embed-core:10.1.25"));
        assertTrue(out.contains("via spring-boot-starter-web"));
        assertTrue(out.contains("fixes 1 of 1"));
        assertTrue(out.contains("springcli upgrade --to 3.3.2"));
    }

    @Test
    void noSuggestionWhenNoUpgradeHelps(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), BOOT_POM);
        FakeOsv osv = new FakeOsv().batch("GHSA-tc01").batch("GHSA-tc01").vuln("GHSA-tc01", "HIGH");

        int[] code = new int[1];
        String out = run(code, command(osv, resolver(List.of(OLD_TOMCAT), List.of(OLD_TOMCAT))),
                "--file", pom.toString());

        assertEquals(1, code[0]);
        assertFalse(out.contains("upgrade --to"));
    }

    @Test
    void failOnHighIgnoresModerateFindings(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);
        FakeOsv osv = new FakeOsv().batch("GHSA-m").vuln("GHSA-m", "MODERATE", "CVE-2024-38809");

        int[] code = new int[1];
        String out = run(code, command(osv, failingResolver()),
                "--no-resolve", "--fail-on", "high", "--file", pom.toString());

        assertEquals(0, code[0]);
        assertTrue(out.contains("CVE-2024-38809"), "still reported, just not failing");
    }

    @Test
    void unknownSeverityFailsEvenAtCritical(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);
        FakeOsv osv = new FakeOsv().batch("CVE-2024-9").vuln("CVE-2024-9", null);

        int[] code = new int[1];
        run(code, command(osv, failingResolver()), "--no-resolve", "--fail-on", "critical", "--file", pom.toString());

        assertEquals(1, code[0]);
    }

    @Test
    void jsonReportIsTheOnlyThingOnStdout(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), BOOT_POM);

        int[] code = new int[1];
        String out = run(code, command(tomcatVulnerableUntilUpgrade(), resolver(List.of(OLD_TOMCAT), List.of(NEW_TOMCAT))),
                "--format", "json", "--file", pom.toString());

        JsonNode json = new ObjectMapper().readTree(out); // throws if progress text leaked into stdout
        assertEquals(1, code[0]);
        assertEquals("3.2.8", json.path("springBootVersion").asText());
        assertEquals("tomcat-embed-core", json.path("findings").path(0).path("artifactId").asText());
        assertEquals("HIGH", json.path("findings").path(0).path("vulnerabilities").path(0).path("severity").asText());
        assertEquals("3.3.2", json.path("upgradeSuggestion").path("to").asText());
    }

    @Test
    void sarifReportHasARulePerVulnerability(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), BOOT_POM);

        int[] code = new int[1];
        String out = run(code, command(tomcatVulnerableUntilUpgrade(), resolver(List.of(OLD_TOMCAT), List.of(NEW_TOMCAT))),
                "--format", "sarif", "--file", pom.toString());

        JsonNode run = new ObjectMapper().readTree(out).path("runs").path(0);
        assertEquals("GHSA-tc01", run.path("tool").path("driver").path("rules").path(0).path("id").asText());
        assertEquals("8.0", run.path("tool").path("driver").path("rules").path(0)
                .path("properties").path("security-severity").asText());
        assertEquals("GHSA-tc01", run.path("results").path(0).path("ruleId").asText());
        assertEquals("error", run.path("results").path(0).path("level").asText());
    }

    @Test
    void resolutionFailureIsAUsageErrorNotAFinding(@TempDir Path dir) throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), BOOT_POM);

        int[] code = new int[1];
        run(code, command(new FakeOsv(), failingResolver()), "--file", pom.toString());

        assertEquals(2, code[0]); // 1 is reserved for "vulnerabilities found"
    }
}
