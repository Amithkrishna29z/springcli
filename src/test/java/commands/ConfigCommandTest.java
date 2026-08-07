package commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import service.ConfigService;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigCommandTest {

    /** Runs `config` against a temp-file-backed service and returns [exitCode, stdout]. */
    private Object[] run(Path file, String... args) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        int code;
        try {
            code = new CommandLine(new ConfigCommand(new ConfigService(file))).execute(args);
        } finally {
            System.setOut(original);
        }
        return new Object[]{code, buf.toString(StandardCharsets.UTF_8)};
    }

    @Test
    void setThenGetReturnsValue(@TempDir Path dir) {
        Path file = dir.resolve("config.json");
        assertEquals(0, run(file, "set", "groupId", "com.acme")[0]);
        Object[] got = run(file, "get", "groupId");
        assertEquals(0, got[0]);
        assertTrue(((String) got[1]).contains("com.acme"));
    }

    @Test
    void listShowsSavedValuesAndPath(@TempDir Path dir) {
        Path file = dir.resolve("config.json");
        run(file, "set", "javaVersion", "21");
        Object[] listed = run(file, "list");
        String out = (String) listed[1];
        assertTrue(out.contains("javaVersion"));
        assertTrue(out.contains("21"));
        assertTrue(out.contains("config.json"));
    }

    @Test
    void unsetRemovesValue(@TempDir Path dir) {
        Path file = dir.resolve("config.json");
        run(file, "set", "groupId", "com.acme");
        assertEquals(0, run(file, "unset", "groupId")[0]);
        Object[] got = run(file, "get", "groupId");
        assertTrue(((String) got[1]).contains("(unset)"));
    }

    @Test
    void dependenciesAreStoredAsList(@TempDir Path dir) {
        Path file = dir.resolve("config.json");
        run(file, "set", "dependencies", "web,data-jpa");
        Object[] got = run(file, "get", "dependencies");
        assertTrue(((String) got[1]).contains("web,data-jpa"));
    }

    @Test
    void pathPrintsFileLocation(@TempDir Path dir) {
        Path file = dir.resolve("config.json");
        Object[] r = run(file, "path");
        assertEquals(0, r[0]);
        assertTrue(((String) r[1]).contains("config.json"));
    }

    @Test
    void unknownKeyIsRejected(@TempDir Path dir) {
        assertNotEquals(0, run(dir.resolve("config.json"), "set", "nope", "x")[0]);
    }

    @Test
    void setWithoutValueIsRejected(@TempDir Path dir) {
        assertNotEquals(0, run(dir.resolve("config.json"), "set", "groupId")[0]);
    }

    @Test
    void unknownActionIsRejected(@TempDir Path dir) {
        assertNotEquals(0, run(dir.resolve("config.json"), "frobnicate")[0]);
    }

    @Test
    void emptyConfigListsNone(@TempDir Path dir) {
        Object[] r = run(dir.resolve("config.json"), "list");
        assertEquals(0, r[0]);
        assertTrue(((String) r[1]).contains("none set"));
    }
}
