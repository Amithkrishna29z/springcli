package commands;

import cli.Main;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandParsingTest {

    private CommandLine root() {
        return new CommandLine(new Main());
    }

    @Test
    void registersAllSubcommands() {
        CommandSpec spec = root().getCommandSpec();
        assertTrue(spec.subcommands().keySet().containsAll(
                List.of("new", "search", "list", "version", "doctor")));
    }

    @Test
    void newCommandParsesPositionalNameAndFlags() {
        CommandLine.ParseResult pr = root().parseArgs(
                "new", "my-app", "--yes", "--group", "com.acme",
                "--deps", "web,data-jpa", "--java-version", "21", "--force");

        CommandLine.ParseResult sub = pr.subcommand();
        assertEquals("new", sub.commandSpec().name());
        NewCommand cmd = (NewCommand) sub.commandSpec().userObject();
        assertNotNull(cmd);

        assertTrue(sub.hasMatchedOption("--yes"));
        assertTrue(sub.hasMatchedOption("--force"));
        assertEquals("com.acme", sub.matchedOptionValue("--group", null));
        assertEquals(List.of("web", "data-jpa"), sub.matchedOptionValue("--deps", List.of()));
    }

    @Test
    void searchRequiresATerm() {

        CommandLine cli = root();
        assertEquals(2, cli.execute("search"));
    }

    @Test
    void versionSubcommandParses() {
        CommandLine.ParseResult pr = root().parseArgs("version");
        assertEquals("version", pr.subcommand().commandSpec().name());
    }
}
