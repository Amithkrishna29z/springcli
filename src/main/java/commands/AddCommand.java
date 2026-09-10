package commands;

import service.DependencyResolver;
import service.PomEditor;
import service.PomFile;
import util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * {@code springcli add <deps>} — add dependencies to an existing Maven project's {@code pom.xml}.
 *
 * <p>The dependency ids are resolved to their exact Maven coordinates by {@link DependencyResolver},
 * then the new {@code <dependency>} nodes are injected into the target file (skipping any already
 * present).
 */
@Command(name = "add", description = "Add dependencies to an existing Maven project's pom.xml.")
public class AddCommand implements Callable<Integer> {

    @Parameters(arity = "1..*", paramLabel = "<deps>",
            description = "Dependency ids to add, e.g. 'redis actuator' (space or comma separated).")
    private List<String> depArgs;

    @Option(names = "--dry-run", description = "Show what would be added without modifying the file.")
    private boolean dryRun;

    @Mixin
    private PomFileOption pomFileOption;

    private final DependencyResolver dependencyResolver;
    private final PomEditor pomEditor = new PomEditor();

    public AddCommand(DependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    @Override
    public Integer call() {
        PomFile pom = pomFileOption.load();
        List<PomEditor.Dep> resolved = dependencyResolver.resolve(DependencyArgs.parse(depArgs, "add"));

        Set<String> present = pomEditor.dependencyKeys(pom.xml());
        List<PomEditor.Dep> toAdd = resolved.stream()
                .filter(d -> !present.contains(d.key()))
                .toList();

        if (toAdd.isEmpty()) {
            Ansi.warn("Nothing to add — the requested dependencies are already in " + pom.fileName() + ".");
            return 0;
        }

        if (dryRun) {
            System.out.println("\nWould add to " + pom.path() + ":\n");
            System.out.print(pomEditor.renderBlock(pom.xml(), toAdd));
            return 0;
        }

        pom.write(pomEditor.addDependencies(pom.xml(), toAdd));
        for (PomEditor.Dep d : toAdd) {
            Ansi.success("Added " + d.key());
        }
        int skipped = resolved.size() - toAdd.size();
        if (skipped > 0) {
            System.out.println("  (" + skipped + " already present, skipped)");
        }
        return 0;
    }
}
