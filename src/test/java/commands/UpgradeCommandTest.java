package commands;

import cli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import service.DependencyUpgrader;
import support.FakeMavenCentral;
import support.SampleMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeCommandTest {

    /** SampleMetadata's latest (default) Boot version is 3.3.2; 3.2.8 is also a valid value. */
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

    /** Pins lombok and guava directly, and both jjwt artifacts through a shared property. */
    private static final String PINNED_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.3.2</version>
                </parent>
                <artifactId>demo</artifactId>
                <properties>
                    <java.version>17</java.version>
                    <jjwt.version>0.11.5</jjwt.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>1.18.30</version>
                        <optional>true</optional>
                    </dependency>
                    <dependency>
                        <groupId>io.jsonwebtoken</groupId>
                        <artifactId>jjwt-api</artifactId>
                        <version>${jjwt.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>io.jsonwebtoken</groupId>
                        <artifactId>jjwt-impl</artifactId>
                        <version>${jjwt.version}</version>
                        <scope>runtime</scope>
                    </dependency>
                    <dependency>
                        <groupId>com.google.guava</groupId>
                        <artifactId>guava</artifactId>
                        <version>32.1.3-jre</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    /** jjwt-impl lags behind jjwt-api, so their shared version can only move to 0.12.6. */
    private static FakeMavenCentral central() {
        return new FakeMavenCentral()
                .artifact("org.projectlombok:lombok", "1.18.30", "1.18.38", "2.0.0-beta1")
                .artifact("io.jsonwebtoken:jjwt-api", "0.11.5", "0.12.6", "0.13.0")
                .artifact("io.jsonwebtoken:jjwt-impl", "0.11.5", "0.12.6")
                .artifact("com.google.guava:guava", "32.1.3-jre", "33.4.8-android", "33.4.8-jre", "33.5.0-android");
    }

    private String run(int[] code, Path pom, String... extraArgs) {
        return run(code, pom, new FakeMavenCentral(), extraArgs);
    }

    private String run(int[] code, Path pom, FakeMavenCentral central, String... extraArgs) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            UpgradeCommand cmd = new UpgradeCommand(SampleMetadata.service(), new DependencyUpgrader(central.service()));
            String[] args = new String[extraArgs.length + 2];
            args[0] = "--file";
            args[1] = pom.toString();
            System.arraycopy(extraArgs, 0, args, 2, extraArgs.length);
            code[0] = Main.configure(new CommandLine(cmd)).execute(args);
        } finally {
            System.setOut(original);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void bumpsToLatestAndWritesTheFile(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), pomWithBoot("3.2.8"));

        int[] code = new int[1];
        String out = run(code, pom);

        assertEquals(0, code[0]);
        assertTrue(out.toLowerCase().contains("upgraded"));
        assertTrue(Files.readString(pom).contains("<version>3.3.2</version>"));
        assertFalse(Files.readString(pom).contains("3.2.8"));
    }

    @Test
    void dryRunReportsButDoesNotWrite(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), pomWithBoot("3.2.8"));

        int[] code = new int[1];
        String out = run(code, pom, "--dry-run");

        assertEquals(0, code[0]);
        assertTrue(out.toLowerCase().contains("would change"));
        assertTrue(Files.readString(pom).contains("<version>3.2.8</version>")); // untouched
    }

    @Test
    void upgradeToSpecificVersion(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), pomWithBoot("3.3.2"));

        int[] code = new int[1];
        String out = run(code, pom, "--to", "3.2.8"); // explicit target is honored even if older

        assertEquals(0, code[0]);
        assertTrue(out.toLowerCase().contains("upgraded"));
        assertTrue(Files.readString(pom).contains("<version>3.2.8</version>"));
    }

    @Test
    void invalidTargetVersionIsRejected(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), pomWithBoot("3.2.8"));

        int[] code = new int[1];
        run(code, pom, "--to", "9.9.9");

        assertNotEquals(0, code[0]); // rejected before any write
        assertTrue(Files.readString(pom).contains("<version>3.2.8</version>")); // untouched
    }

    @Test
    void alreadyOnLatestIsANoOp(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), pomWithBoot("3.3.2"));

        int[] code = new int[1];
        String out = run(code, pom);

        assertEquals(0, code[0]);
        assertTrue(out.toLowerCase().contains("nothing to upgrade"));
    }

    @Test
    void errorsWhenNoSpringBootParent(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"),
                "<project><artifactId>demo</artifactId></project>");

        int[] code = new int[1];
        run(code, pom);

        assertEquals(2, code[0]);
    }

    @Test
    void depsUpdatesPinnedVersionsToTheLatestStableRelease(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);

        int[] code = new int[1];
        String out = run(code, pom, central(), "--deps");

        assertEquals(0, code[0]);
        // Only the pinned versions change: lombok skips the beta, guava stays on -jre, and the shared
        // jjwt property moves only as far as jjwt-impl has been released. Spring Boot is left alone.
        assertEquals(PINNED_POM
                        .replace("<version>1.18.30</version>", "<version>1.18.38</version>")
                        .replace("<jjwt.version>0.11.5</jjwt.version>", "<jjwt.version>0.12.6</jjwt.version>")
                        .replace("<version>32.1.3-jre</version>", "<version>33.4.8-jre</version>"),
                Files.readString(pom));
        assertTrue(out.contains("(major)")); // 0.11 → 0.12 and 32 → 33
        assertTrue(out.contains("Updated 4 dependencies"));
    }

    @Test
    void depsDryRunReportsButDoesNotWrite(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);

        int[] code = new int[1];
        String out = run(code, pom, central(), "--deps", "--dry-run");

        assertEquals(0, code[0]);
        assertTrue(out.contains("1.18.38"));
        assertTrue(out.toLowerCase().contains("would update"));
        assertEquals(PINNED_POM, Files.readString(pom));
    }

    @Test
    void depsCanBeLimitedToNamedDependencies(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);

        int[] code = new int[1];
        run(code, pom, central(), "--deps", "lombok", "io.jsonwebtoken:jjwt-impl");

        assertEquals(0, code[0]);
        String xml = Files.readString(pom);
        assertTrue(xml.contains("<version>1.18.38</version>"));
        assertTrue(xml.contains("<jjwt.version>0.12.6</jjwt.version>"), "jjwt-impl shares the property");
        assertTrue(xml.contains("<version>32.1.3-jre</version>"), "guava wasn't named");
    }

    @Test
    void namingAnUnpinnedDependencyIsAUsageError(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);

        int[] code = new int[1];
        run(code, pom, central(), "--deps", "spring-boot-starter-web"); // managed by Spring Boot

        assertEquals(2, code[0]);
        assertEquals(PINNED_POM, Files.readString(pom));
    }

    @Test
    void depsSkipsArtifactsMissingFromMavenCentral(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);
        FakeMavenCentral withoutGuava = new FakeMavenCentral()
                .artifact("org.projectlombok:lombok", "1.18.30", "1.18.38")
                .artifact("io.jsonwebtoken:jjwt-api", "0.11.5")
                .artifact("io.jsonwebtoken:jjwt-impl", "0.11.5");

        int[] code = new int[1];
        String out = run(code, pom, withoutGuava, "--deps");

        assertEquals(0, code[0]);
        assertTrue(out.contains("isn't on Maven Central"));
        String xml = Files.readString(pom);
        assertTrue(xml.contains("<version>1.18.38</version>"));
        assertTrue(xml.contains("<version>32.1.3-jre</version>"));
        assertTrue(xml.contains("<jjwt.version>0.11.5</jjwt.version>")); // already the latest
    }

    @Test
    void depsWithNothingPinnedLeavesSpringBootAlone(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), pomWithBoot("3.2.8"));

        int[] code = new int[1];
        String out = run(code, pom, "--deps");

        assertEquals(0, code[0]);
        assertTrue(out.toLowerCase().contains("spring boot manages"));
        assertTrue(Files.readString(pom).contains("<version>3.2.8</version>"));
    }

    @Test
    void namingDependenciesRequiresDeps(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);

        int[] code = new int[1];
        run(code, pom, central(), "lombok");

        assertEquals(2, code[0]);
        assertEquals(PINNED_POM, Files.readString(pom));
    }

    @Test
    void toCannotBeCombinedWithDeps(@TempDir Path dir) throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), PINNED_POM);

        int[] code = new int[1];
        run(code, pom, central(), "--deps", "--to", "3.2.8");

        assertEquals(2, code[0]);
        assertEquals(PINNED_POM, Files.readString(pom));
    }
}
