package cli;

import commands.DoctorCommand;
import commands.ListCommand;
import commands.NewCommand;
import commands.SearchCommand;
import commands.VersionCommand;
import exception.SpringCliException;
import util.Ansi;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Application entry point and root command. Wires the subcommands, prints the banner, and installs a
 * global exception handler that renders {@link SpringCliException}s as clean, coloured messages
 * (reserving stack traces for genuinely unexpected failures).
 */
@Command(
        name = "springcli",
        mixinStandardHelpOptions = true,
        versionProvider = Main.VersionProvider.class,
        description = "Scaffold Spring Boot projects using the Spring Initializr API.",
        subcommands = {
                NewCommand.class,
                SearchCommand.class,
                ListCommand.class,
                VersionCommand.class,
                DoctorCommand.class
        }
)
public class Main implements Runnable {

    private static final String BANNER = """
             ___              _              ___ _    ___
            / __|_ __ _ _(_)_ _  __ _ / __| |  |_ _|
            \\__ \\ '_ \\ '_| | ' \\/ _` | (__| |__ | |
            |___/ .__/_| |_|_||_\\__, |\\___|____|___|
                |_|             |___/
            """;

    @Option(names = "--no-banner", description = "Suppress the ASCII banner.")
    private boolean noBanner;

    /**
     * When invoked with no subcommand, show the banner and usage help.
     */
    @Override
    public void run() {
        printBanner();
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        forceUtf8Console();

        Main root = new Main();
        CommandLine cmd = new CommandLine(root)
                .setExecutionExceptionHandler(new FriendlyExceptionHandler())
                .setCaseInsensitiveEnumValuesAllowed(true);

        // Show the banner for real work (not for help/version, which picocli handles directly).
        if (args.length > 0 && !isHelpOrVersion(args[0]) && !contains(args, "--no-banner")) {
            root.printBanner();
        }

        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    /**
     * Ensures console output uses UTF-8 so box-drawing and check-mark glyphs render correctly on
     * Windows terminals that still default to a legacy code page.
     */
    private static void forceUtf8Console() {
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8));
    }

    private void printBanner() {
        if (!noBanner) {
            System.out.println(Ansi.cyan(BANNER));
        }
    }

    private static boolean isHelpOrVersion(String arg) {
        return arg.equals("-h") || arg.equals("--help")
                || arg.equals("-V") || arg.equals("--version") || arg.equals("version");
    }

    private static boolean contains(String[] args, String flag) {
        for (String a : args) {
            if (a.equals(flag)) {
                return true;
            }
        }
        return false;
    }

    /** Renders application errors cleanly; falls back to a stack trace for unexpected ones. */
    static class FriendlyExceptionHandler implements CommandLine.IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(Exception ex, CommandLine cmd, CommandLine.ParseResult parseResult) {
            if (ex instanceof SpringCliException) {
                Ansi.error(ex.getMessage());
                return 1;
            }
            Ansi.error("Unexpected error: " + ex.getMessage());
            if (System.getenv("SPRINGCLI_DEBUG") != null) {
                ex.printStackTrace();
            } else {
                System.err.println("Run with SPRINGCLI_DEBUG=1 for a full stack trace.");
            }
            return 2;
        }
    }

    /** Supplies {@code --version} output from the single source of truth. */
    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{"springcli " + VersionCommand.VERSION};
        }
    }
}
