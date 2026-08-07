package util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnsiTest {

    @Test
    void colourHelpersPreserveText() {
        assertTrue(Ansi.green("hello").contains("hello"));
        assertTrue(Ansi.red("err").contains("err"));
        assertTrue(Ansi.yellow("warn").contains("warn"));
        assertTrue(Ansi.cyan("info").contains("info"));
        assertTrue(Ansi.bold("bold").contains("bold"));
    }

    @Test
    void successAndInfoWriteToStdout() {
        PrintStream original = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            Ansi.success("created");
            Ansi.info("working");
        } finally {
            System.setOut(original);
        }
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("created"));
        assertTrue(out.contains("working"));
    }

    @Test
    void errorWritesToStderr() {
        PrintStream original = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            Ansi.error("boom");
        } finally {
            System.setErr(original);
        }
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("boom"));
    }
}
