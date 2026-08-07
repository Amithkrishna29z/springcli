package commands;

import cli.Main;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionAndGuideTest {

    private String executeCapturingStdout(String... args) {
        PrintStream original = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            assertEquals(0, new CommandLine(new Main()).execute(args));
        } finally {
            System.setOut(original);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void completionEmitsABashScriptForSpringcli() {
        String script = executeCapturingStdout("completion");
        // The generated bash completion registers a function for the springcli command.
        assertTrue(script.contains("springcli"), "script should reference the command name");
        assertTrue(script.contains("complete -F") || script.contains("_complete_springcli"),
                "script should define a bash completion function");
    }

    @Test
    void completionScriptListsSubcommands() {
        String script = executeCapturingStdout("completion");
        assertTrue(script.contains("new"));
        assertTrue(script.contains("config"));
    }

    @Test
    void guideMentionsCommonCommands() {
        String guide = executeCapturingStdout("guide");
        assertTrue(guide.contains("springcli new"));
        assertTrue(guide.contains("springcli config"));
        assertTrue(guide.contains("springcli completion"));
    }
}
