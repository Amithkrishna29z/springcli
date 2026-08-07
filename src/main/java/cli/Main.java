package cli;

import commands.CompletionCommand;
import commands.ConfigCommand;
import commands.DoctorCommand;
import commands.GuideCommand;
import commands.ListCommand;
import commands.ModifyCommand;
import commands.NewCommand;
import commands.SearchCommand;
import commands.UpdateCommand;
import commands.VersionCommand;
import exception.SpringCliException;
import service.UpdateNotifier;
import service.UpdateService;
import util.Ansi;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "springcli",
        mixinStandardHelpOptions = true,
        versionProvider = Main.VersionProvider.class,
        description = "Scaffold Spring Boot projects using the Spring Initializr API.",
        footerHeading = "%nExamples:%n",
        footer = {
                "  springcli new my-app       Create a project with the interactive wizard",
                "  springcli search web       Find dependencies by keyword",
                "  springcli config set groupId com.acme   Save a default",
                "",
                "New here? Run 'springcli guide' for a getting-started walkthrough."
        },
        subcommands = {
                NewCommand.class,
                SearchCommand.class,
                ListCommand.class,
                ConfigCommand.class,
                GuideCommand.class,
                CompletionCommand.class,
                UpdateCommand.class,
                ModifyCommand.class,
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

        if (args.length > 0 && !isHelpOrVersion(args[0]) && !contains(args, "--no-banner")) {
            root.printBanner();
        }

        int exitCode = cmd.execute(args);
        maybeNotifyUpdate(args);
        System.exit(exitCode);
    }

    /**
     * Best-effort startup update notice. Skipped for non-interactive output (pipes/scripts), for the
     * update/completion/version commands themselves, and when SPRINGCLI_NO_UPDATE_CHECK is set. The
     * underlying check is throttled and time-bounded, so this never meaningfully delays a command.
     */
    private static void maybeNotifyUpdate(String[] args) {
        if (System.getenv("SPRINGCLI_NO_UPDATE_CHECK") != null || System.console() == null) {
            return;
        }
        String command = firstNonOption(args);
        if (command == null || command.equals("update") || command.equals("modify")
                || command.equals("completion") || command.equals("version")) {
            return;
        }
        try {
            UpdateService service = new UpdateService(VersionCommand.VERSION);
            java.nio.file.Path cache = java.nio.file.Path.of(
                    System.getProperty("user.home"), ".springcli", "update-check.json");
            new UpdateNotifier(service, cache, java.time.Duration.ofHours(24)).maybeNotify(System.err);
        } catch (Exception ignored) {
            // never fail a command over an update notice
        }
    }

    private static String firstNonOption(String[] args) {
        for (String a : args) {
            if (!a.startsWith("-")) {
                return a;
            }
        }
        return null;
    }

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

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{"springcli " + VersionCommand.VERSION};
        }
    }
}
