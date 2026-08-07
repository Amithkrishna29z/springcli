package commands;

import cli.Main;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandExecutionTest {

    @Test
    void versionCommandPrintsVersionAndReturnsZero() {
        PrintStream original = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        int code;
        try {
            code = new VersionCommand().call();
        } finally {
            System.setOut(original);
        }
        assertEquals(0, code);
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains(VersionCommand.VERSION));
    }

    @Test
    void unknownSubcommandIsAUsageError() {
        assertNotEquals(0, new CommandLine(new Main()).execute("bogus"));
    }

    @Test
    void rootHelpReturnsZero() {
        assertEquals(0, new CommandLine(new Main()).execute("--help"));
    }

    @Test
    void rootHelpListsSubcommands() {
        PrintStream original = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            new CommandLine(new Main()).execute("--help");
        } finally {
            System.setOut(original);
        }
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("new"));
        assertTrue(out.contains("search"));
        assertTrue(out.contains("doctor"));
    }
}
