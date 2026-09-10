package commands;

import cli.Main;
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

    private String run(int[] code, Path pom, String... extraArgs) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            UpgradeCommand cmd = new UpgradeCommand(SampleMetadata.service());
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
}
