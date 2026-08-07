package commands;

import util.Ansi;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * {@code springcli guide} — a friendly getting-started page for new users, summarising the common
 * commands with copy-pasteable examples. Complements {@code --help}, which is the exhaustive
 * reference.
 */
@Command(name = "guide", description = "Show a getting-started guide for new users.")
public class GuideCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println(Ansi.bold("springcli — getting started") + "\n");
        System.out.println("Scaffold Spring Boot projects using the official Spring Initializr API.\n");

        section("Create a project");
        line("springcli new my-app", "Interactive wizard (best for first-timers)");
        line("springcli new my-app --yes", "Non-interactive, using your defaults + flags");
        line("springcli new my-app --yes --deps web,data-jpa", "Pick dependencies up front");

        section("Explore dependencies");
        line("springcli search web", "Find dependencies by keyword");
        line("springcli list", "List all dependencies by category");

        section("Save your preferences (used by every 'new')");
        line("springcli config set groupId com.acme", "Remember your group id");
        line("springcli config set javaVersion 21", "…and Java version, etc.");
        line("springcli config list", "Show what's saved");

        section("Tab completion (bash/zsh)");
        line("source <(springcli completion)", "Enable completion in the current shell");

        section("Other");
        line("springcli doctor", "Check your Java / Maven / Git setup");
        line("springcli --help", "Full command reference");

        System.out.println("\nDocs: " + Ansi.cyan("https://github.com/Amithkrishna29z/springcli"));
        return 0;
    }

    private void section(String title) {
        System.out.println("\n" + Ansi.bold(title));
    }

    private void line(String command, String description) {
        // Pad on the uncoloured length so columns line up even when ANSI colours are enabled.
        String pad = " ".repeat(Math.max(1, 46 - command.length()));
        System.out.println("  " + Ansi.green(command) + pad + description);
    }
}
