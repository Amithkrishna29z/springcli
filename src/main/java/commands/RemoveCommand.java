package commands;

import service.DependencyResolver;
import service.PomEditor;
import service.PomFile;
import util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * {@code springcli remove <deps>} — remove dependencies from an existing Maven project's
 * {@code pom.xml}. The inverse of {@link AddCommand}: dependency ids are resolved to their exact Maven
 * coordinates by {@link DependencyResolver}, then any matching {@code <dependency>} nodes are deleted
 * from the target file (leaving everything else untouched).
 */
@Command(name = "remove", description = "Remove dependencies from an existing Maven project's pom.xml.")
public class RemoveCommand implements Callable<Integer> {

    @Parameters(arity = "1..*", paramLabel = "<deps>",
            description = "Dependency ids to remove, e.g. 'redis actuator' (space or comma separated).")
    private List<String> depArgs;

    @Option(names = "--dry-run", description = "Show what would be removed without modifying the file.")
    private boolean dryRun;

    @Mixin
    private PomFileOption pomFileOption;

    private final DependencyResolver dependencyResolver;
    private final PomEditor pomEditor = new PomEditor();

    public RemoveCommand(DependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    @Override
    public Integer call() {
        PomFile pom = pomFileOption.load();
        List<PomEditor.Dep> resolved = dependencyResolver.resolve(DependencyArgs.parse(depArgs, "remove"));

        Set<String> present = pomEditor.dependencyKeys(pom.xml());
        Set<String> toRemove = resolved.stream()
                .map(PomEditor.Dep::key)
                .filter(present::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (toRemove.isEmpty()) {
            Ansi.warn("Nothing to remove — none of the requested dependencies are in "
                    + pom.fileName() + ".");
            return 0;
        }

        if (dryRun) {
            System.out.println("\nWould remove from " + pom.path() + ":\n");
            for (String key : toRemove) {
                System.out.println("  " + key);
            }
            return 0;
        }

        pom.write(pomEditor.removeDependencies(pom.xml(), toRemove));
        for (String key : toRemove) {
            Ansi.success("Removed " + key);
        }
        return 0;
    }
}
